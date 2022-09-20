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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Consumer;

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
 * Bind a GraphQL argument, or the full arguments map, onto a target object.
 *
 * <p>Binding is performed by mapping argument values to a primary data
 * constructor of the target object, or by using a default constructor and
 * mapping argument values to its properties. This is applied recursively.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlArgumentBinder {

	/**
	 * Use a larger {@link DataBinder#DEFAULT_AUTO_GROW_COLLECTION_LIMIT} for GraphQL use cases
	 */
	private static final int DEFAULT_AUTO_GROW_COLLECTION_LIMIT = 1024;

	@Nullable
	private final SimpleTypeConverter typeConverter;

	private final BindingErrorProcessor bindingErrorProcessor = new DefaultBindingErrorProcessor();

	private List<Consumer<DataBinder>> dataBinderInitializers = new ArrayList<>();


	public GraphQlArgumentBinder() {
		this(null);
	}

	public GraphQlArgumentBinder(@Nullable ConversionService conversionService) {
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
	 * Add a {@link DataBinder} consumer that initializes the binder instance before the binding process.
	 * @param dataBinderInitializer the data binder initializer
	 * @since 1.0.1
	 */
	public void addDataBinderInitializer(Consumer<DataBinder> dataBinderInitializer) {
		this.dataBinderInitializers.add(dataBinderInitializer);
	}


	/**
	 * Bind a single argument, or the full arguments map, onto an object of the
	 * given target type.
	 * @param environment for access to the arguments
	 * @param argumentName the name of the argument to bind, or {@code null} to
	 * use the full arguments map
	 * @param targetType the type of Object to create
	 * @return the created Object, possibly {@code null}
	 * @throws BindException in case of binding issues such as conversion errors,
	 * mismatches between the source and the target object structure, and so on.
	 * Binding issues are accumulated as {@link BindException#getFieldErrors()
	 * field errors} where the {@link FieldError#getField() field} of each error
	 * is the argument path where the issue occurred.
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	public Object bind(
			DataFetchingEnvironment environment, @Nullable String argumentName, ResolvableType targetType)
			throws BindException {

		Object rawValue = (argumentName != null ?
				environment.getArgument(argumentName) : environment.getArguments());

		if (rawValue == null) {
			return wrapAsOptionalIfNecessary(null, targetType);
		}

		Class<?> targetClass = targetType.resolve();
		Assert.notNull(targetClass, "Could not determine target type from " + targetType);

		DataBinder binder = new DataBinder(null, argumentName != null ? argumentName : "arguments");
		initDataBinder(binder);
		BindingResult bindingResult = binder.getBindingResult();
		Stack<String> segments = new Stack<>();

		try {
			// From Collection

			if (isApproximableCollectionType(rawValue)) {
				segments.push(argumentName);
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

	private void initDataBinder(DataBinder binder) {
		binder.setAutoGrowCollectionLimit(DEFAULT_AUTO_GROW_COLLECTION_LIMIT);
		this.dataBinderInitializers.forEach(initializer -> initializer.accept(binder));
	}

	@Nullable
	private Object wrapAsOptionalIfNecessary(@Nullable Object value, ResolvableType type) {
		return (type.resolve(Object.class).equals(Optional.class) ? Optional.ofNullable(value) : value);
	}

	private boolean isApproximableCollectionType(@Nullable Object rawValue) {
		return (rawValue != null &&
				(CollectionFactory.isApproximableCollectionType(rawValue.getClass()) ||
						rawValue instanceof List));  // it may be SingletonList
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

		Collection<T> collection = CollectionFactory.createCollection(collectionType.getRawClass(), elementClass, rawCollection.size());
		int i = 0;
		for (Object rawValue : rawCollection) {
			segments.push("[" + i++ + "]");
			if (rawValue == null || elementClass.isAssignableFrom(rawValue.getClass())) {
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

		// Default constructor + data binding via properties

		if (ctor.getParameterCount() == 0) {
			target = BeanUtils.instantiateClass(ctor);
			DataBinder dataBinder = new DataBinder(target);
			initDataBinder(dataBinder);
			dataBinder.getBindingResult().setNestedPath(toArgumentPath(segments));
			dataBinder.setConversionService(getConversionService());
			dataBinder.bind(initBindValues(rawMap));

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
			else if (paramTypes[i] == Object.class) {
				args[i] = rawValue;
			}
			else if (isApproximableCollectionType(rawValue)) {
				ResolvableType elementType = ResolvableType.forMethodParameter(methodParam);
				args[i] = createCollection((Collection<Object>) rawValue, elementType, bindingResult, segments);
			}
			else if (rawValue instanceof Map) {
				boolean isOptional = (paramTypes[i] == Optional.class);
				Class<?> type = (isOptional ? methodParam.nestedIfOptional().getNestedParameterType() : paramTypes[i]);
				Object value = createValueOrNull((Map<String, Object>) rawValue, type, bindingResult, segments);
				args[i] = (isOptional ? Optional.ofNullable(value) : value);
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

	private MutablePropertyValues initBindValues(Map<String, Object> rawMap) {
		MutablePropertyValues mpvs = new MutablePropertyValues();
		Stack<String> segments = new Stack<>();
		for (String key : rawMap.keySet()) {
			addBindValues(mpvs, key, rawMap.get(key), segments);
		}
		return mpvs;
	}

	@SuppressWarnings("unchecked")
	private void addBindValues(MutablePropertyValues mpvs, String name, Object value, Stack<String> segments) {
		if (value instanceof List) {
			List<Object> items = (List<Object>) value;
			if (items.isEmpty()) {
				segments.push(name);
				mpvs.add(toArgumentPath(segments), value);
				segments.pop();
			}
			else {
				for (int i = 0; i < items.size(); i++) {
					addBindValues(mpvs, name + "[" + i + "]", items.get(i), segments);
				}
			}
		}
		else if (value instanceof Map) {
			segments.push(name + ".");
			Map<String, Object> map = (Map<String, Object>) value;
			for (String key : map.keySet()) {
				addBindValues(mpvs, key, map.get(key), segments);
			}
			segments.pop();
		}
		else {
			segments.push(name);
			mpvs.add(toArgumentPath(segments), value);
			segments.pop();
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
