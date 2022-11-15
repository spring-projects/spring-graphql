/*
 * Copyright 2020-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql.data.method.annotation.support;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;

import org.springframework.aop.SpringProxy;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.projection.TargetAware;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import static org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY;

/**
 * {@link BeanFactoryInitializationAotProcessor} implementation for registering
 * runtime hints discoverable through GraphQL controllers, such as:
 * <ul>
 *     <li>invocation reflection on {@code @SchemaMapping} and {@code @BatchMapping} annotated controllers methods
 *     <li>binding reflection on controller method arguments, needed for binding or by the GraphQL Java engine itself
 *     <li>reflection for SpEL support and JDK proxy creation for {@code @ProjectedPayload} projections,
 *     if Spring Data Commons is present on the classpath.
 * </ul>
 * <p>This processor is using a {@link HandlerMethodArgumentResolver} resolution mechanism similar
 * to the one used in {@link AnnotatedControllerConfigurer}. The type of runtime hints registered
 * for each method argument depends on the {@link HandlerMethodArgumentResolver} resolved.
 * <p>Manual registration of {@link graphql.schema.DataFetcher} cannot be detected by this
 * processor; developers will need to declare bound types with {@link RegisterReflectionForBinding}
 * annotations on their configuration class.
 *
 * @author Brian Clozel
 * @see org.springframework.graphql.data.method.HandlerMethodArgumentResolver
 * @since 1.1.0
 */
class SchemaMappingBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

	private final static boolean springDataPresent = ClassUtils.isPresent(
			"org.springframework.data.projection.SpelAwareProxyProjectionFactory",
			SchemaMappingBeanFactoryInitializationAotProcessor.class.getClassLoader());


	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		Class<?>[] controllerTypes = Arrays.stream(beanFactory.getBeanDefinitionNames())
				.map(beanName -> RegisteredBean.of(beanFactory, beanName).getBeanClass())
				.filter(this::isController)
				.toArray(Class<?>[]::new);
		return new SchemaMappingBeanFactoryInitializationAotContribution(controllerTypes);
	}

	private boolean isController(AnnotatedElement element) {
		return MergedAnnotations.from(element, TYPE_HIERARCHY).isPresent(Controller.class);
	}

	private static class SchemaMappingBeanFactoryInitializationAotContribution implements BeanFactoryInitializationAotContribution {

		private final Class<?>[] controllers;

		private final HandlerMethodArgumentResolverComposite argumentResolvers;

		public SchemaMappingBeanFactoryInitializationAotContribution(Class<?>[] controllers) {
			this.controllers = controllers;
			this.argumentResolvers = createArgumentResolvers();
		}

		private HandlerMethodArgumentResolverComposite createArgumentResolvers() {
			AnnotatedControllerConfigurer controllerConfigurer = new AnnotatedControllerConfigurer();
			controllerConfigurer.setApplicationContext(new StaticApplicationContext());
			controllerConfigurer.afterPropertiesSet();
			HandlerMethodArgumentResolverComposite argumentResolverComposite = controllerConfigurer.getArgumentResolvers();
			Assert.notNull(argumentResolverComposite, "argument resolvers should not be null");
			return argumentResolverComposite;
		}

		@Override
		public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
			RuntimeHints runtimeHints = generationContext.getRuntimeHints();
			registerSpringDataSpelSupport(runtimeHints);
			Arrays.stream(this.controllers).forEach(controller -> {
				runtimeHints.reflection().registerType(controller);
				ReflectionUtils.doWithMethods(controller, method -> processSchemaMappingMethod(runtimeHints, method), this::isGraphQlHandlerMethod);
			});
		}

		private void registerSpringDataSpelSupport(RuntimeHints runtimeHints) {
			if (springDataPresent) {
				runtimeHints.reflection()
						.registerType(SpelAwareProxyProjectionFactory.class)
						.registerType(TypeReference.of("org.springframework.data.projection.SpelEvaluatingMethodInterceptor$TargetWrapper"),
								builder -> builder.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
										MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS));
			}
		}

		private boolean isGraphQlHandlerMethod(AnnotatedElement element) {
			MergedAnnotations mergedAnnotations = MergedAnnotations.from(element, TYPE_HIERARCHY);
			return mergedAnnotations.isPresent(SchemaMapping.class)
					|| mergedAnnotations.isPresent(BatchMapping.class);
		}

		private void processSchemaMappingMethod(RuntimeHints runtimeHints, Method method) {
			runtimeHints.reflection().registerMethod(method, ExecutableMode.INVOKE);
			for (Parameter parameter : method.getParameters()) {
				processMethodParameter(runtimeHints, MethodParameter.forParameter(parameter));
			}
			processReturnType(runtimeHints, MethodParameter.forExecutable(method, -1));
		}

		private void processMethodParameter(RuntimeHints runtimeHints, MethodParameter methodParameter) {
			MethodParameterRuntimeHintsRegistrar.fromMethodParameter(this.argumentResolvers, methodParameter)
					.apply(runtimeHints);
		}

		private void processReturnType(RuntimeHints runtimeHints, MethodParameter methodParameter) {
			new ArgumentBindingHints(methodParameter).apply(runtimeHints);
		}

	}

	@FunctionalInterface
	private interface MethodParameterRuntimeHintsRegistrar {

		BindingReflectionHintsRegistrar bindingRegistrar = new BindingReflectionHintsRegistrar();

		void apply(RuntimeHints runtimeHints);

		static MethodParameterRuntimeHintsRegistrar fromMethodParameter(HandlerMethodArgumentResolverComposite argumentResolvers, MethodParameter methodParameter) {
			HandlerMethodArgumentResolver argumentResolver = argumentResolvers.getArgumentResolver(methodParameter);
			if (argumentResolver instanceof ArgumentMethodArgumentResolver
					|| argumentResolver instanceof ArgumentsMethodArgumentResolver) {
				return new ArgumentBindingHints(methodParameter);
			}
			if (argumentResolver instanceof DataLoaderMethodArgumentResolver) {
				return new DataLoaderHints(methodParameter);
			}
			if (springDataPresent) {
				if (argumentResolver instanceof ProjectedPayloadMethodArgumentResolver) {
					return new ProjectedPayloadHints(methodParameter);
				}
			}
			return new NoHintsRequired();
		}

	}

	private static class NoHintsRequired implements MethodParameterRuntimeHintsRegistrar {

		@Override
		public void apply(RuntimeHints runtimeHints) {
			// no runtime hints are required for this type of argument
		}
	}

	private static class ArgumentBindingHints implements MethodParameterRuntimeHintsRegistrar {

		private final MethodParameter methodParameter;

		public ArgumentBindingHints(MethodParameter methodParameter) {
			this.methodParameter = methodParameter;
		}

		@Override
		public void apply(RuntimeHints runtimeHints) {
			Type parameterType = this.methodParameter.getGenericParameterType();
			if (ArgumentValue.class.isAssignableFrom(methodParameter.getParameterType())) {
				parameterType = this.methodParameter.nested().getNestedGenericParameterType();
			}
			bindingRegistrar.registerReflectionHints(runtimeHints.reflection(), parameterType);
		}
	}

	private static class DataLoaderHints implements MethodParameterRuntimeHintsRegistrar {

		private final MethodParameter methodParameter;

		public DataLoaderHints(MethodParameter methodParameter) {
			this.methodParameter = methodParameter;
		}

		@Override
		public void apply(RuntimeHints runtimeHints) {
			bindingRegistrar.registerReflectionHints(runtimeHints.reflection(),
					this.methodParameter.nested().getNestedGenericParameterType());
		}
	}

	private static class ProjectedPayloadHints implements MethodParameterRuntimeHintsRegistrar {

		private final MethodParameter methodParameter;

		public ProjectedPayloadHints(MethodParameter methodParameter) {
			this.methodParameter = methodParameter;
		}

		@Override
		public void apply(RuntimeHints runtimeHints) {
			Class<?> parameterType = this.methodParameter.nestedIfOptional().getNestedParameterType();
			runtimeHints.reflection().registerType(parameterType);
			runtimeHints.proxies().registerJdkProxy(parameterType, TargetAware.class, SpringProxy.class, DecoratingProxy.class);
		}
	}

}
