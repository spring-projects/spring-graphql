/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.graphql.data;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.NotWritablePropertyException;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.CollectionFactory;
import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.util.DirectFieldAccessFallbackBeanWrapper;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.AbstractBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;


/**
 * Binder that instantiates and populates a target Object to reflect the
 * complete structure of the {@link DataFetchingEnvironment#getArguments()
 * GraphQL arguments} input map.
 *
 * <p>The input map is navigated recursively to create the full structure of
 * the target type. Objects in the target type are created either through a
 * primary, data constructor, in which case arguments are matched to constructor
 * parameters by name, or through the default constructor, in which case
 * arguments are matched to properties. Scalar values are converted, if
 * necessary, through a {@link ConversionService}.
 *
 * <p>The binder does not stop at the first error, but rather accumulates as
 * many errors as it can in a {@link org.springframework.validation.BindingResult}.
 * At the end it raises a {@link BindException} that contains all recorded
 * errors along with the path at which each error occurred.
 *
 * <p>The binder supports {@link Optional} as a wrapper around any Object or
 * scalar value in the target Object structure. In addition, it also supports
 * {@link ArgumentValue} as a wrapper that indicates whether a given input
 * argument was omitted rather than set to the {@literal "null"} literal.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlArgumentBinder {

	private final @Nullable SimpleTypeConverter typeConverter;

	private final @Nullable NameResolver nameResolver;

	private final boolean fallBackOnDirectFieldAccess;


	/**
	 * Default constructor.
	 */
	public GraphQlArgumentBinder() {
		this(Options.create());
	}

	/**
	 * Constructor with additional flag for direct field access support.
	 * @param conversionService the service to use
	 * @deprecated in favor of {@link #GraphQlArgumentBinder(Options)}
	 */
	@Deprecated(since = "2.0", forRemoval = true)
	public GraphQlArgumentBinder(@Nullable ConversionService conversionService) {
		this(Options.create().conversionService(conversionService));
	}

	/**
	 * Constructor with additional flag for direct field access support.
	 * @param service the service to use
	 * @param fallBackOnDirectFieldAccess whether to fall back on direct field access
	 * @deprecated in favor of {@link #GraphQlArgumentBinder(Options)}
	 */
	@Deprecated(since = "2.0", forRemoval = true)
	public GraphQlArgumentBinder(@Nullable ConversionService service, boolean fallBackOnDirectFieldAccess) {
		this(Options.create().conversionService(service).fallBackOnDirectFieldAccess(fallBackOnDirectFieldAccess));
	}

	public GraphQlArgumentBinder(Options options) {
		this.typeConverter = initTypeConverter(options.conversionService());
		this.nameResolver = options.nameResolver();
		this.fallBackOnDirectFieldAccess = options.fallBackOnDirectFieldAccess();
	}

	private static @Nullable SimpleTypeConverter initTypeConverter(@Nullable ConversionService service) {
		if (service == null) {
			//  Not thread-safe when using PropertyEditors
			return null;
		}
		SimpleTypeConverter typeConverter = new SimpleTypeConverter();
		typeConverter.setConversionService(service);
		return typeConverter;
	}


	/**
	 * Create and populate an Object of the given target type, from a single
	 * GraphQL argument, or from the full GraphQL arguments map.
	 * @param environment for access to the arguments
	 * @param name the name of an argument, or {@code null} to use the full map
	 * @param targetType the type of Object to create
	 * @return the created Object, possibly wrapped in {@link Optional} or in
	 * {@link ArgumentValue}, or {@code null} if there is no value
	 * @throws BindException containing one or more accumulated errors from
	 * matching and/or converting arguments to the target Object
	 */
	public @Nullable Object bind(
			DataFetchingEnvironment environment, @Nullable String name, ResolvableType targetType)
			throws BindException {

		Object rawValue = (name != null) ? environment.getArgument(name) : environment.getArguments();
		boolean isOmitted = (name != null && !environment.getArguments().containsKey(name));

		return bind(rawValue, isOmitted, targetType);
	}

	/**
	 * Variant of {@link #bind(DataFetchingEnvironment, String, ResolvableType)}
	 * with a pre-extracted raw value to bind from.
	 * @param rawValue the raw argument value (Collection, Map, or scalar)
	 * @param isOmitted {@code true} if the argument was omitted from the input
	 * and {@code false} if it was provided, but possibly {@code null}
	 * @param targetType the type of Object to create
	 * @since 1.3.0
	 */
	public @Nullable Object bind(@Nullable Object rawValue, boolean isOmitted, ResolvableType targetType) throws BindException {
		ArgumentsBindingResult bindingResult = new ArgumentsBindingResult(targetType);
		Class<?> targetClass = targetType.resolve(Object.class);
		Object value = bindRawValue("$", rawValue, isOmitted, targetType, targetClass, bindingResult);
		if (bindingResult.hasErrors()) {
			throw new BindException(bindingResult);
		}
		return value;
	}

	/**
	 * Create an Object from the given raw GraphQL argument value.
	 * @param name the name of the constructor parameter or the property that
	 * will be set from the returned value, possibly {@code "$"} for the top
	 * Object, or an indexed property for a Collection element or Map value;
	 * mainly for error recording, to keep track of the nested path
	 * @param rawValue the raw argument value (Collection, Map, or scalar)
	 * @param isOmitted {@code true} if the argument was omitted from the input
	 * and {@code false} if it was provided, but possibly {@code null}
	 * @param targetType the type of Object to create
	 * @param targetClass the target class, resolved from the targetType
	 * @param bindingResult to accumulate errors
	 * @return the target Object instance, possibly {@code null} if the source
	 * value is {@code null}, or if binding failed in which case the result will
	 * contain errors; generally we keep going as far as we can and only raise
	 * a {@link BindException} at the end to record as many errors as possible
	 */
	@SuppressWarnings({"ConstantConditions", "unchecked"})
	private @Nullable Object bindRawValue(
			String name, @Nullable Object rawValue, boolean isOmitted,
			ResolvableType targetType, Class<?> targetClass, ArgumentsBindingResult bindingResult) {

		boolean isOptional = (targetClass == Optional.class);
		boolean isArgumentValue = (targetClass == ArgumentValue.class);

		if (isOptional || isArgumentValue) {
			targetType = targetType.getNested(2);
			targetClass = targetType.resolve();
			Assert.state(targetClass != null, "Could not resolve target type for: " + targetType);
		}

		if (this.nameResolver != null) {
			name = this.nameResolver.resolveName(name);
		}

		Object value;
		if (rawValue == null || targetClass == Object.class) {
			value = rawValue;
		}
		else if (rawValue instanceof Collection) {
			value = bindCollection(name, (Collection<Object>) rawValue, targetType, targetClass, bindingResult);
		}
		else if (rawValue instanceof Map) {
			value = bindMap(name, (Map<String, Object>) rawValue, targetType, targetClass, bindingResult);
		}
		else {
			value = (!targetClass.isAssignableFrom(rawValue.getClass()) ?
					convertValue(name, rawValue, targetType, targetClass, bindingResult) : rawValue);
		}

		if (isOptional) {
			value = Optional.ofNullable(value);
		}
		else if (isArgumentValue) {
			value = (isOmitted ? ArgumentValue.omitted() : ArgumentValue.ofNullable(value));
		}

		return value;
	}

	private Collection<?> bindCollection(
			String name, Collection<Object> rawCollection, ResolvableType collectionType, Class<?> collectionClass,
			ArgumentsBindingResult bindingResult) {

		ResolvableType elementType = collectionType.asCollection().getGeneric(0);
		Class<?> elementClass = collectionType.asCollection().getGeneric(0).resolve();
		if (elementClass == null) {
			bindingResult.rejectArgumentValue(name, null, "unknownType", "Unknown Collection element type");
			return Collections.emptyList(); // Keep going, to record more errors
		}

		Collection<Object> collection =
				CollectionFactory.createCollection(collectionClass, elementClass, rawCollection.size());

		int index = 0;
		for (Object rawValue : rawCollection) {
			String indexedName = name + "[" + index++ + "]";
			collection.add(bindRawValue(indexedName, rawValue, false, elementType, elementClass, bindingResult));
		}

		return collection;
	}

	private @Nullable Object bindMap(
			String name, Map<String, Object> rawMap, ResolvableType targetType, Class<?> targetClass,
			ArgumentsBindingResult bindingResult) {

		if (Map.class.isAssignableFrom(targetClass)) {
			return bindMapToMap(name, rawMap, targetType, targetClass, bindingResult);
		}

		bindingResult.pushNestedPath(name);

		Constructor<?> constructor = BeanUtils.getResolvableConstructor(targetClass);

		Object value = (constructor.getParameterCount() > 0) ?
				bindViaConstructorAndSetters(constructor, rawMap, targetType, bindingResult) :
				bindViaSetters(constructor, rawMap, targetType, bindingResult);

		bindingResult.popNestedPath();

		return value;
	}

	private Map<?, Object> bindMapToMap(
			String name, Map<String, Object> rawMap, ResolvableType targetType, Class<?> targetClass,
			ArgumentsBindingResult bindingResult) {

		ResolvableType valueType = targetType.asMap().getGeneric(1);
		Class<?> valueClass = valueType.resolve(Object.class);
		if (valueClass == Object.class) {
			return rawMap;
		}

		Map<String, Object> map = CollectionFactory.createMap(targetClass, rawMap.size());

		for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
			String key = entry.getKey();
			String indexedName = name + "[" + key + "]";
			map.put(key, bindRawValue(indexedName, entry.getValue(), false, valueType, valueClass, bindingResult));
		}

		return map;
	}

	private @Nullable Object bindViaConstructorAndSetters(Constructor<?> constructor,
			Map<String, Object> rawMap, ResolvableType ownerType, ArgumentsBindingResult bindingResult) {

		@Nullable String[] paramNames = BeanUtils.getParameterNames(constructor);
		Class<?>[] paramTypes = constructor.getParameterTypes();
		@Nullable Object[] constructorArguments = new Object[paramTypes.length];

		for (int i = 0; i < paramNames.length; i++) {
			String name = paramNames[i];
			Assert.notNull(name, () -> "Missing parameter name in " + constructor);

			ResolvableType targetType = ResolvableType.forType(
					ResolvableType.forConstructorParameter(constructor, i).getType(), ownerType);

			Object rawValue = rawMap.get(name);
			boolean isNotPresent = !rawMap.containsKey(name);

			if (rawValue == null && this.nameResolver != null) {
				for (String key : rawMap.keySet()) {
					if (this.nameResolver.resolveName(key).equals(name)) {
						rawValue = rawMap.get(key);
						isNotPresent = false;
						break;
					}
				}
			}

			constructorArguments[i] = bindRawValue(
					name, rawValue, isNotPresent, targetType, paramTypes[i], bindingResult);
		}

		Object target;
		try {
			target = BeanUtils.instantiateClass(constructor, constructorArguments);
		}
		catch (BeanInstantiationException ex) {
			// Ignore, if we had binding errors to begin with
			if (bindingResult.hasErrors()) {
				return null;
			}
			throw ex;
		}

		// If no errors, apply setters too
		if (!bindingResult.hasErrors()) {
			bindViaSetters(target, rawMap, ownerType, bindingResult);
		}

		return target;
	}

	private Object bindViaSetters(Constructor<?> constructor,
			Map<String, Object> rawMap, ResolvableType ownerType, ArgumentsBindingResult bindingResult) {

		Object target = BeanUtils.instantiateClass(constructor);
		bindViaSetters(target, rawMap, ownerType, bindingResult);
		return target;
	}

	private void bindViaSetters(Object target,
			Map<String, Object> rawMap, ResolvableType ownerType, ArgumentsBindingResult bindingResult) {

		BeanWrapper beanWrapper = (this.fallBackOnDirectFieldAccess ?
				new DirectFieldAccessFallbackBeanWrapper(target) : PropertyAccessorFactory.forBeanPropertyAccess(target));

		for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
			String key = entry.getKey();
			if (this.nameResolver != null) {
				key = this.nameResolver.resolveName(key);
			}
			TypeDescriptor typeDescriptor = beanWrapper.getPropertyTypeDescriptor(key);
			if (typeDescriptor == null && this.fallBackOnDirectFieldAccess) {
				Field field = ReflectionUtils.findField(beanWrapper.getWrappedClass(), key);
				if (field != null) {
					typeDescriptor = new TypeDescriptor(field);
				}
			}
			if (typeDescriptor == null) {
				// Ignore unknown property
				continue;
			}

			ResolvableType targetType =
					ResolvableType.forType(typeDescriptor.getResolvableType().getType(), ownerType);

			Object value = bindRawValue(
					key, entry.getValue(), false, targetType, typeDescriptor.getType(), bindingResult);

			try {
				if (value != null) {
					beanWrapper.setPropertyValue(key, value);
				}
			}
			catch (NotWritablePropertyException ex) {
				// Ignore unknown property
			}
			catch (Exception ex) {
				bindingResult.rejectArgumentValue(key, value, "invalidPropertyValue", "Failed to set property value");
			}
		}
	}

	@SuppressWarnings("unchecked")
	private @Nullable <T> T convertValue(
			String name, @Nullable Object rawValue, ResolvableType type, Class<T> clazz,
			ArgumentsBindingResult bindingResult) {

		Object value = null;
		try {
			TypeConverter converter =
					(this.typeConverter != null) ? this.typeConverter : new SimpleTypeConverter();

			value = converter.convertIfNecessary(
					rawValue, (Class<?>) clazz, new TypeDescriptor(type, null, null));
		}
		catch (TypeMismatchException ex) {
			bindingResult.rejectArgumentValue(name, rawValue, ex.getErrorCode(), "Failed to convert argument value");
		}

		return (T) value;
	}


	/**
	 * Container of configuration settings for {@link GraphQlArgumentBinder}.
	 * @since 2.0.0
	 */
	public static final class Options {

		private final @Nullable ConversionService conversionService;

		private final @Nullable NameResolver nameResolver;

		private final boolean fallBackOnDirectFieldAccess;

		private Options(@Nullable ConversionService conversionService, @Nullable NameResolver nameResolver,
				boolean fallBackOnDirectFieldAccess) {

			this.conversionService = conversionService;
			this.nameResolver = nameResolver;
			this.fallBackOnDirectFieldAccess = fallBackOnDirectFieldAccess;
		}

		/**
		 * Add a {@link ConversionService} to apply type conversion to argument
		 * values where needed.
		 * @param service the service to use
		 */
		public Options conversionService(@Nullable ConversionService service) {
			return new Options(service, this.nameResolver, this.fallBackOnDirectFieldAccess);
		}

		/**
		 * Add a resolver to help to map GraphQL argument names to Object property names.
		 * @param resolver the resolver to add
		 */
		public Options nameResolver(NameResolver resolver) {
			resolver = ((this.nameResolver != null) ? this.nameResolver.andThen(resolver) : resolver);
			return new Options(this.conversionService, resolver, this.fallBackOnDirectFieldAccess);
		}

		/**
		 * Whether binding GraphQL arguments onto
		 * {@link org.springframework.graphql.data.method.annotation.Argument @Argument}
		 * should falls back to direct field access in case the target object does
		 * not use accessor methods.
		 * @param fallBackOnDirectFieldAccess whether to fall back on direct field access
		 */
		public Options fallBackOnDirectFieldAccess(boolean fallBackOnDirectFieldAccess) {
			return new Options(this.conversionService, this.nameResolver, fallBackOnDirectFieldAccess);
		}

		public @Nullable ConversionService conversionService() {
			return this.conversionService;
		}

		public @Nullable NameResolver nameResolver() {
			return this.nameResolver;
		}

		public boolean fallBackOnDirectFieldAccess() {
			return this.fallBackOnDirectFieldAccess;
		}

		/**
		 * Create an instance without any options set.
		 */
		public static Options create() {
			return new Options(null, (name) -> name, false);
		}
	}


	/**
	 * Contract to customize the mapping of GraphQL argument names to Object
	 * properties. This can be useful for dealing with naming conventions like
	 * the use of "-" that cannot be used in Java property names.
	 * @since 2.0.0
	 */
	public interface NameResolver {

		/**
		 * Resolve the given GraphQL argument name to an Object property name.
		 * @param name the argument name
		 * @return the resolved name to use
		 */
		String resolveName(String name);

		/**
		 * Append another resolver to be invoked after the current one.
		 * @param resolver the resolver to invoked
		 * @return a new composite resolver
		 */
		default NameResolver andThen(NameResolver resolver) {
			return (name) -> resolver.resolveName(resolveName(name));
		}
	}


	/**
	 * Subclass of {@link AbstractBindingResult} that doesn't have a target Object,
	 * and takes the raw value as input when recording errors.
	 */
	@SuppressWarnings("serial")
	private static class ArgumentsBindingResult extends AbstractBindingResult {

		ArgumentsBindingResult(ResolvableType targetType) {
			super(initObjectName(targetType));
		}

		private static String initObjectName(ResolvableType targetType) {
			return (targetType.getSource() instanceof MethodParameter methodParameter) ?
					Conventions.getVariableNameForParameter(methodParameter) :
					ClassUtils.getShortNameAsProperty(targetType.resolve(Object.class));
		}

		@Override
		public @Nullable Object getTarget() {
			return null;
		}

		@Override
		protected @Nullable Object getActualFieldValue(String field) {
			return null;
		}

		void rejectArgumentValue(
				String field, @Nullable Object rawValue, String code, String defaultMessage) {

			addError(new FieldError(
					getObjectName(), fixedField(field), rawValue, true, resolveMessageCodes(code),
					null, defaultMessage));
		}
	}

}
