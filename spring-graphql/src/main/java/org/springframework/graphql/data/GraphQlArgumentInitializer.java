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

package org.springframework.graphql.data;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import graphql.schema.DataFetchingEnvironment;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingErrorProcessor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DataBinder;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.validation.FieldError;


/**
 * Bind GraphQL arguments to higher level objects. 
 * 
 * <p>The target object may have 
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlArgumentInitializer {

	@Nullable
	private final SimpleTypeConverter typeConverter;

	private final BindingErrorProcessor bindingErrorProcessor = new DefaultBindingErrorProcessor();


	public GraphQlArgumentInitializer(@Nullable ConversionService conversionService) {
		if (conversionService != null) {
			this.typeConverter = new SimpleTypeConverter();
			this.typeConverter.setConversionService(conversionService);
		}
		else {
			//  Not thread-safe when using PropertyEditors
			this.typeConverter = null;
		}
	}


	private SimpleTypeConverter getTypeConverter() {
		return (this.typeConverter != null ? this.typeConverter : new SimpleTypeConverter());
	}

	@Nullable
	private ConversionService getConversionService() {
		return (this.typeConverter != null ? this.typeConverter.getConversionService() : null);
	}


	/**
	 * Initialize an Object of the given {@code targetType}, either from a named
	 * {@link DataFetchingEnvironment#getArgument(String) argument value}, or from all
	 * {@link DataFetchingEnvironment#getArguments() values} as the source.
	 * @param environment the environment with the argument values
	 * @param name optionally, the name of an argument to initialize from,
	 * or if {@code null}, the full map of arguments is used.
	 * @param targetType the type of Object to initialize
	 * @return the initialized Object, or {@code null}
	 * @throws BindException raised in case of issues with binding argument values
	 * such as conversion errors, type mismatches between the source values and
	 * the target type structure, etc.
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	public Object initializeArgument(
			DataFetchingEnvironment environment, @Nullable String name, ResolvableType targetType) throws BindException {

		Object rawValue = (name != null ? environment.getArgument(name) : environment.getArguments());

		if (rawValue == null) {
			return wrapAsOptionalIfNecessary(null, targetType);
		}

		Class<?> targetClass = targetType.resolve();
		Assert.notNull(targetClass, "Could not determine target type from " + targetType);

		DataBinder binder = new DataBinder(null, name != null ? name : "arguments");
		BindingResult bindingResult = binder.getBindingResult();
		Stack<String> segments = new Stack<>();

		try {
			// From Collection

			if (CollectionFactory.isApproximableCollectionType(rawValue.getClass())) {
				segments.push(name);
				return createCollection((Collection<Object>) rawValue, targetType, bindingResult, segments);
			}

			if (targetClass == Optional.class) {
				targetClass = targetType.getNested(2).resolve();
				Assert.notNull(targetClass, "Could not determine Optional<T> type from " + targetType);
			}

			// From Map

			if (rawValue instanceof Map) {
				Object target = createValue((Map<String, Object>) rawValue, targetClass, bindingResult, segments);
				return wrapAsOptionalIfNecessary(target, targetType);
			}

			// From Scalar

			if (targetClass.isInstance(rawValue)) {
				return wrapAsOptionalIfNecessary(rawValue, targetType);
			}

			Object target = convertValue(rawValue, targetClass, bindingResult, segments);
			return wrapAsOptionalIfNecessary(target, targetType);
		}
		finally {
			checkBindingResult(bindingResult);
		}
	}

	@Nullable
	private Object wrapAsOptionalIfNecessary(@Nullable Object value, ResolvableType type) {
		return (type.resolve(Object.class).equals(Optional.class) ? Optional.ofNullable(value) : value);
	}

	@SuppressWarnings({"ConstantConditions", "unchecked"})
	private <T> Collection<T> createCollection(
			Collection<Object> rawCollection, ResolvableType collectionType,
			BindingResult bindingResult, Stack<String> segments) {

		if (!Collection.class.isAssignableFrom(collectionType.resolve())) {
			bindingResult.rejectValue(toArgumentPath(segments), "typeMismatch", "Expected collection: " + collectionType);
			return Collections.emptyList();
		}

		Class<?> elementClass = collectionType.asCollection().getGeneric(0).resolve();
		if (elementClass == null) {
			bindingResult.rejectValue(toArgumentPath(segments), "unknownElementType", "Unknown element type");
			return Collections.emptyList();
		}

		Collection<T> collection = CollectionFactory.createApproximateCollection(rawCollection, rawCollection.size());
		int i = 0;
		for (Object rawValue : rawCollection) {
			segments.push("[" + i++ + "]");
			if (elementClass.isAssignableFrom(rawValue.getClass())) {
				collection.add((T) rawValue);
			}
			else if (rawValue instanceof Map) {
				collection.add((T) createValueOrNull((Map<String, Object>) rawValue, elementClass, bindingResult, segments));
			}
			else {
				collection.add((T) convertValue(rawValue, elementClass, bindingResult, segments));
			}
			segments.pop();
		}
		return collection;
	}

	@Nullable
	private Object createValueOrNull(
			Map<String, Object> rawMap, Class<?> targetType, BindingResult result, Stack<String> segments) {

		try {
			return createValue(rawMap, targetType, result, segments);
		}
		catch (BindException ex) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private Object createValue(
			Map<String, Object> rawMap, Class<?> targetType, BindingResult bindingResult,
			Stack<String> segments) throws BindException {

		Object target;
		Constructor<?> ctor = BeanUtils.getResolvableConstructor(targetType);

		// Default constructor with data binding

		if (ctor.getParameterCount() == 0) {
			MutablePropertyValues mpvs = new MutablePropertyValues();
			visitArgumentMap(rawMap, mpvs, new Stack<>());

			target = BeanUtils.instantiateClass(ctor);
			DataBinder dataBinder = new DataBinder(target);
			dataBinder.getBindingResult().setNestedPath(toArgumentPath(segments));
			dataBinder.setConversionService(getConversionService());
			dataBinder.bind(mpvs);

			if (dataBinder.getBindingResult().hasErrors()) {
				addErrors(dataBinder, bindingResult, segments);
				throw new BindException(bindingResult);
			}

			return target;
		}

		// Data class constructor

		if (!segments.isEmpty()) {
			segments.push(".");
		}

		String[] paramNames = BeanUtils.getParameterNames(ctor);
		Class<?>[] paramTypes = ctor.getParameterTypes();
		Object[] args = new Object[paramTypes.length];

		for (int i = 0; i < paramNames.length; i++) {
			String paramName = paramNames[i];
			Object rawValue = rawMap.get(paramName);
			segments.push(paramName);
			MethodParameter methodParam = new MethodParameter(ctor, i);
			if (rawValue == null && methodParam.isOptional()) {
				args[i] = (paramTypes[i] == Optional.class ? Optional.empty() : null);
			}
			else if (rawValue != null && CollectionFactory.isApproximableCollectionType(rawValue.getClass())) {
				ResolvableType elementType = ResolvableType.forMethodParameter(methodParam);
				args[i] = createCollection((Collection<Object>) rawValue, elementType, bindingResult, segments);
			}
			else if (rawValue instanceof Map) {
				args[i] = createValueOrNull((Map<String, Object>) rawValue, paramTypes[i], bindingResult, segments);
			}
			else {
				args[i] = convertValue(rawValue, paramTypes[i], new TypeDescriptor(methodParam), bindingResult, segments);
			}
			segments.pop();
		}

		if (segments.size() > 1) {
			segments.pop();
		}

		try {
			return BeanUtils.instantiateClass(ctor, args);
		}
		catch (BeanInstantiationException ex) {
			// Swallow if we had binding errors, it's as far as we could go
			checkBindingResult(bindingResult);
			throw ex;
		}
	}

	@SuppressWarnings("unchecked")
	private void visitArgumentMap(Map<String, Object> rawMap, MutablePropertyValues mpvs, Stack<String> segments) {
		for (String key : rawMap.keySet()) {
			Object rawValue = rawMap.get(key);
			if (rawValue instanceof List) {
				List<Object> items = (List<Object>) rawValue;
				if (items.isEmpty()) {
					segments.push(key);
					mpvs.add(toArgumentPath(segments), rawValue);
					segments.pop();
				}
				else {
					Map<String, Object> subValues = new HashMap<>(items.size());
					for (int i = 0; i < items.size(); i++) {
						subValues.put(key + "[" + i + "]", items.get(i));
					}
					visitArgumentMap(subValues, mpvs, segments);
				}
			}
			else if (rawValue instanceof Map) {
				segments.push(key + ".");
				visitArgumentMap((Map<String, Object>) rawValue, mpvs, segments);
				segments.pop();
			}
			else {
				segments.push(key);
				mpvs.add(toArgumentPath(segments), rawValue);
				segments.pop();
			}
		}
	}

	private String toArgumentPath(Stack<String> path) {
		StringBuilder sb = new StringBuilder();
		path.forEach(sb::append);
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private <T> T convertValue(@Nullable Object rawValue, Class<T> type, BindingResult result, Stack<String> segments) {
		return (T) convertValue(rawValue, type, TypeDescriptor.valueOf(type), result, segments);
	}

	@Nullable
	private Object convertValue(
			@Nullable Object rawValue, Class<?> type, TypeDescriptor descriptor,
			BindingResult bindingResult, Stack<String> segments) {

		try {
			return getTypeConverter().convertIfNecessary(rawValue, type, descriptor);
		}
		catch (TypeMismatchException ex) {
			String name = toArgumentPath(segments);
			ex.initPropertyName(name);
			bindingResult.recordFieldValue(name, type, rawValue);
			this.bindingErrorProcessor.processPropertyAccessException(ex, bindingResult);
		}
		return null;
	}

	private void addErrors(DataBinder binder, BindingResult bindingResult, Stack<String> segments) {
		String path = (!segments.isEmpty() ? toArgumentPath(segments) + "." : "");
		binder.getBindingResult().getFieldErrors().forEach(error -> bindingResult.addError(
				new FieldError(bindingResult.getObjectName(), path + error.getField(),
						error.getRejectedValue(), error.isBindingFailure(), error.getCodes(),
						error.getArguments(), error.getDefaultMessage())));
	}

	private void checkBindingResult(BindingResult bindingResult) throws BindException {
		if (bindingResult.hasErrors()) {
			throw new BindException(bindingResult);
		}
	}

}
