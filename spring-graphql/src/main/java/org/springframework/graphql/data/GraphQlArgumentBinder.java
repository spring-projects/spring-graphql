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
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
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

	private final List<Consumer<DataBinder>> dataBinderInitializers = new ArrayList<>();


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
	 * @param name the name of the argument to bind, or {@code null} to
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
	public Object bind(
			DataFetchingEnvironment environment, @Nullable String name, ResolvableType targetType)
			throws BindException {

		Object rawValue = (name != null ?
				environment.getArgument(name) : environment.getArguments());

		DataBinder binder = new DataBinder(null, name != null ? ("Arguments[" + name + "]") : "Arguments");
		initDataBinder(binder);
		BindingResult bindingResult = binder.getBindingResult();

		Stack<String> segments = new Stack<>();
		if (name != null) {
			segments.push(name);
		}

		Object targetValue = bindRawValue(
				rawValue, targetType, targetType.resolve(Object.class), bindingResult, segments);

		if (bindingResult.hasErrors()) {
			throw new BindException(bindingResult);
		}

		return targetValue;
	}

	private void initDataBinder(DataBinder binder) {
		binder.setAutoGrowCollectionLimit(DEFAULT_AUTO_GROW_COLLECTION_LIMIT);
		this.dataBinderInitializers.forEach(initializer -> initializer.accept(binder));
	}

	@SuppressWarnings({"ConstantConditions", "unchecked"})
	@Nullable
	private Object bindRawValue(
			Object rawValue, ResolvableType targetType, Class<?> targetClass,
			BindingResult bindingResult, Stack<String> segments) {

		boolean isOptional = (targetClass == Optional.class);

		if (isOptional) {
			targetType = targetType.getNested(2);
			targetClass = targetType.resolve();
		}

		Object value;
		if (rawValue == null || targetClass == Object.class) {
			value = rawValue;
		}
		else if (rawValue instanceof Collection) {
			value = bindCollection((Collection<Object>) rawValue, targetType, targetClass, bindingResult, segments);
		}
		else if (rawValue instanceof Map) {
			value = bindMap((Map<String, Object>) rawValue, targetType, targetClass, bindingResult, segments);
		}
		else {
			value = (targetClass.isAssignableFrom(rawValue.getClass()) ?
					rawValue : convertValue(rawValue, targetClass, bindingResult, segments));
		}

		return (isOptional ? Optional.ofNullable(value) : value);
	}

	private Collection<?> bindCollection(
			Collection<Object> rawCollection, ResolvableType collectionType, Class<?> collectionClass,
			BindingResult bindingResult, Stack<String> segments) {

		ResolvableType elementType = collectionType.asCollection().getGeneric(0);
		Class<?> elementClass = collectionType.asCollection().getGeneric(0).resolve();
		if (elementClass == null) {
			bindingResult.rejectValue(toArgumentPath(segments), "unknownTargetType", "Unknown target type");
			return Collections.emptyList(); // Keep going, report as many errors as we can
		}

		Collection<Object> collection =
				CollectionFactory.createCollection(collectionClass, elementClass, rawCollection.size());

		int index = 0;
		for (Object rawValue : rawCollection) {
			segments.push("[" + index++ + "]");
			collection.add(bindRawValue(rawValue, elementType, elementClass, bindingResult, segments));
			segments.pop();
		}

		return collection;
	}

	private static String toArgumentPath(Stack<String> path) {
		StringBuilder sb = new StringBuilder();
		path.forEach(sb::append);
		return sb.toString();
	}

	@Nullable
	private Object bindMap(
			Map<String, Object> rawMap, ResolvableType targetType, Class<?> targetClass,
			BindingResult bindingResult, Stack<String> segments) {

		if (Map.class.isAssignableFrom(targetClass)) {
			return bindMapToMap(rawMap, targetType, bindingResult, segments, targetClass);
		}

		Constructor<?> constructor = BeanUtils.getResolvableConstructor(targetClass);
		if (constructor.getParameterCount() > 0) {
			return bindMapToObjectViaConstructor(rawMap, constructor, bindingResult, segments);
		}

		Object target = BeanUtils.instantiateClass(constructor);
		DataBinder dataBinder = new DataBinder(target);
		initDataBinder(dataBinder);
		dataBinder.getBindingResult().setNestedPath(toArgumentPath(segments));
		dataBinder.setConversionService(getConversionService());
		dataBinder.bind(createPropertyValues(rawMap));

		if (dataBinder.getBindingResult().hasErrors()) {
			String nestedPath = dataBinder.getBindingResult().getNestedPath();
			for (FieldError error : dataBinder.getBindingResult().getFieldErrors()) {
				bindingResult.addError(
						new FieldError(bindingResult.getObjectName(), nestedPath + error.getField(),
								error.getRejectedValue(), error.isBindingFailure(), error.getCodes(),
								error.getArguments(), error.getDefaultMessage()));
			}
			return null;
		}

		return target;
	}

	private Map<?, Object> bindMapToMap(
			Map<String, Object> rawMap, ResolvableType targetType, BindingResult bindingResult,
			Stack<String> segments, Class<?> targetClass) {

		ResolvableType valueType = targetType.asMap().getGeneric(1);
		Class<?> valueClass = valueType.resolve();
		if (valueClass == null) {
			bindingResult.rejectValue(toArgumentPath(segments), "unknownTargetType", "Unknown target type");
			return Collections.emptyMap(); // Keep going, report as many errors as we can
		}

		Map<String, Object> map = CollectionFactory.createMap(targetClass, rawMap.size());

		for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
			String key = entry.getKey();
			segments.push("[" + key + "]");
			map.put(key, bindRawValue(entry.getValue(), valueType, valueClass, bindingResult, segments));
			segments.pop();
		}

		return map;
	}

	@Nullable
	private Object bindMapToObjectViaConstructor(
			Map<String, Object> rawMap, Constructor<?> constructor, BindingResult bindingResult,
			Stack<String> segments) {

		if (segments.size() > 0) {
			segments.push(".");
		}

		String[] paramNames = BeanUtils.getParameterNames(constructor);
		Class<?>[] paramTypes = constructor.getParameterTypes();
		Object[] args = new Object[paramTypes.length];

		for (int i = 0; i < paramNames.length; i++) {
			String name = paramNames[i];
			segments.push(name);
			ResolvableType paramType = ResolvableType.forConstructorParameter(constructor, i);
			args[i] = bindRawValue(rawMap.get(name), paramType, paramTypes[i], bindingResult, segments);
			segments.pop();
		}

		if (segments.size() > 1) {
			segments.pop();
		}

		try {
			return BeanUtils.instantiateClass(constructor, args);
		}
		catch (BeanInstantiationException ex) {
			// Ignore: we had binding errors to begin with
			if (bindingResult.hasErrors()) {
				return null;
			}
			throw ex;
		}
	}

	private static MutablePropertyValues createPropertyValues(Map<String, Object> rawMap) {
		MutablePropertyValues mpvs = new MutablePropertyValues();
		Stack<String> segments = new Stack<>();
		for (String key : rawMap.keySet()) {
			addPropertyValue(mpvs, key, rawMap.get(key), segments);
		}
		return mpvs;
	}

	@SuppressWarnings("unchecked")
	private static void addPropertyValue(MutablePropertyValues mpvs, String name, Object value, Stack<String> segments) {
		if (value instanceof List) {
			List<Object> items = (List<Object>) value;
			if (items.isEmpty()) {
				segments.push(name);
				mpvs.add(toArgumentPath(segments), value);
				segments.pop();
			}
			else {
				for (int i = 0; i < items.size(); i++) {
					addPropertyValue(mpvs, name + "[" + i + "]", items.get(i), segments);
				}
			}
		}
		else if (value instanceof Map) {
			segments.push(name + ".");
			Map<String, Object> map = (Map<String, Object>) value;
			for (String key : map.keySet()) {
				addPropertyValue(mpvs, key, map.get(key), segments);
			}
			segments.pop();
		}
		else {
			segments.push(name);
			mpvs.add(toArgumentPath(segments), value);
			segments.pop();
		}
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private <T> T convertValue(
			@Nullable Object rawValue, Class<T> type, BindingResult bindingResult, Stack<String> segments) {

		Object value = null;
		try {
			value = getTypeConverter().convertIfNecessary(rawValue, (Class<?>) type, TypeDescriptor.valueOf(type));
		}
		catch (TypeMismatchException ex) {
			String name = toArgumentPath(segments);
			ex.initPropertyName(name);
			bindingResult.recordFieldValue(name, type, rawValue);
			this.bindingErrorProcessor.processPropertyAccessException(ex, bindingResult);
		}
		return (T) value;
	}

}
