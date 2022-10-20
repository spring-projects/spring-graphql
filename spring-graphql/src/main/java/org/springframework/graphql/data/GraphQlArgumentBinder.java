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
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import graphql.schema.DataFetchingEnvironment;

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
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.validation.AbstractBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.DataBinder;
import org.springframework.validation.FieldError;


/**
 * Bind a GraphQL argument, or the full arguments map, onto a target object.
 *
 * <p>Complex objects (non-scalar) are initialized either through the primary
 * data constructor where arguments are matched to constructor parameters, or
 * through the default constructor where arguments are matched to setter
 * property methods. In case objects are related to other objects, binding is
 * applied recursively to create nested objects.
 *
 * <p>Scalar values are converted to the expected target type through a
 * {@link ConversionService}, if provided.
 *
 * <p>In case of any errors, when creating objects or converting scalar values,
 * a {@link BindException} is raised that contains all errors recorded along
 * with the path at which the errors occurred.
 *
 * @author Brian Clozel
 * @author Rossen Stoyanchev
 * @since 1.0.0
 */
public class GraphQlArgumentBinder {

	@Nullable
	private final SimpleTypeConverter typeConverter;


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


	/**
	 * Add a {@link DataBinder} consumer that initializes the binder instance
	 * before the binding process.
	 * @param consumer the data binder initializer
	 * @since 1.0.1
	 * @deprecated this property is deprecated, ignored, and should not be
	 * necessary as a {@link DataBinder} is no longer used to bind arguments
	 */
	@Deprecated(since = "1.1.0", forRemoval = true)
	public void addDataBinderInitializer(Consumer<DataBinder> consumer) {
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

		ArgumentsBindingResult bindingResult = new ArgumentsBindingResult(targetType);

		Object value = bindRawValue("$", rawValue, targetType, targetType.resolve(Object.class), bindingResult);

		if (bindingResult.hasErrors()) {
			throw new BindException(bindingResult);
		}

		return value;
	}

	/**
	 * Bind the raw GraphQL argument value to an Object of the specified type.
	 * @param name the name of a constructor parameter or a bean property of the
	 * target Object that is to be initialized from the given raw value;
	 * {@code "$"} if binding the top level Object; possibly indexed if binding
	 * to a Collection element or to a Map value.
	 * @param rawValue the raw argument value (Collection, Map, or scalar)
	 * @param targetType the type of Object to create
	 * @param targetClass the resolved class from the targetType
	 * @param bindingResult for keeping track of the nested path and errors
	 * @return the target Object instance, possibly {@code null} if the source
	 * value is {@code null} or if binding failed in which case the result will
	 * contain errors; nevertheless we keep going to record as many errors as
	 * we can accumulate
	 */
	@SuppressWarnings({"ConstantConditions", "unchecked"})
	@Nullable
	private Object bindRawValue(
			String name, @Nullable Object rawValue, ResolvableType targetType, Class<?> targetClass,
			ArgumentsBindingResult bindingResult) {

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
			value = bindCollection(name, (Collection<Object>) rawValue, targetType, targetClass, bindingResult);
		}
		else if (rawValue instanceof Map) {
			value = bindMap(name, (Map<String, Object>) rawValue, targetType, targetClass, bindingResult);
		}
		else {
			value = (targetClass.isAssignableFrom(rawValue.getClass()) ?
					rawValue : convertValue(name, rawValue, targetClass, bindingResult));
		}

		return (isOptional ? Optional.ofNullable(value) : value);
	}

	private Collection<?> bindCollection(
			String name, Collection<Object> rawCollection, ResolvableType collectionType, Class<?> collectionClass,
			ArgumentsBindingResult bindingResult) {

		ResolvableType elementType = collectionType.asCollection().getGeneric(0);
		Class<?> elementClass = collectionType.asCollection().getGeneric(0).resolve();
		if (elementClass == null) {
			bindingResult.rejectValue(null, "unknownType", "Unknown Collection element type");
			return Collections.emptyList(); // Keep going, report as many errors as we can
		}

		Collection<Object> collection =
				CollectionFactory.createCollection(collectionClass, elementClass, rawCollection.size());

		int index = 0;
		for (Object rawValue : rawCollection) {
			String indexedName = name + "[" + index++ + "]";
			collection.add(bindRawValue(indexedName, rawValue, elementType, elementClass, bindingResult));
		}

		return collection;
	}

	@Nullable
	private Object bindMap(
			String name, Map<String, Object> rawMap, ResolvableType targetType, Class<?> targetClass,
			ArgumentsBindingResult bindingResult) {

		if (Map.class.isAssignableFrom(targetClass)) {
			return bindMapToMap(name, rawMap, targetType, targetClass, bindingResult);
		}

		bindingResult.pushNestedPath(name);

		Constructor<?> constructor = BeanUtils.getResolvableConstructor(targetClass);

		Object value = constructor.getParameterCount() > 0 ?
				bindMapToObjectViaConstructor(rawMap, constructor, bindingResult) :
				bindMapToObjectViaSetters(rawMap, constructor, bindingResult);

		bindingResult.popNestedPath();

		return value;
	}

	private Map<?, Object> bindMapToMap(
			String name, Map<String, Object> rawMap, ResolvableType targetType, Class<?> targetClass,
			ArgumentsBindingResult bindingResult) {

		ResolvableType valueType = targetType.asMap().getGeneric(1);
		Class<?> valueClass = valueType.resolve();
		if (valueClass == null) {
			bindingResult.rejectValue(null, "unknownType", "Unknown Map value type");
			return Collections.emptyMap(); // Keep going, report as many errors as we can
		}

		Map<String, Object> map = CollectionFactory.createMap(targetClass, rawMap.size());

		for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
			String key = entry.getKey();
			String indexedName = name + "[" + key + "]";
			map.put(key, bindRawValue(indexedName, entry.getValue(), valueType, valueClass, bindingResult));
		}

		return map;
	}

	@Nullable
	private Object bindMapToObjectViaConstructor(
			Map<String, Object> rawMap, Constructor<?> constructor, ArgumentsBindingResult bindingResult) {

		String[] paramNames = BeanUtils.getParameterNames(constructor);
		Class<?>[] paramTypes = constructor.getParameterTypes();
		Object[] args = new Object[paramTypes.length];

		for (int i = 0; i < paramNames.length; i++) {
			String name = paramNames[i];
			ResolvableType paramType = ResolvableType.forConstructorParameter(constructor, i);
			args[i] = bindRawValue(name, rawMap.get(name), paramType, paramTypes[i], bindingResult);
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

	private Object bindMapToObjectViaSetters(
			Map<String, Object> rawMap, Constructor<?> constructor, ArgumentsBindingResult bindingResult) {

		Object target = BeanUtils.instantiateClass(constructor);
		BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(target);

		for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
			String key = entry.getKey();
			TypeDescriptor type = beanWrapper.getPropertyTypeDescriptor(key);
			if (type == null) {
				// Ignore unknown property
				continue;
			}
			Object value = bindRawValue(
					key, entry.getValue(), type.getResolvableType(), type.getType(), bindingResult);
			try {
				if (value != null) {
					beanWrapper.setPropertyValue(key, value);
				}
			}
			catch (NotWritablePropertyException ex) {
				// Ignore unknown property
			}
			catch (Exception ex) {
				bindingResult.rejectValue(value, "invalidPropertyValue", "Failed to set property value");
			}
		}

		return target;
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private <T> T convertValue(
			String name, @Nullable Object rawValue, Class<T> type, ArgumentsBindingResult bindingResult) {

		Object value = null;
		try {
			TypeConverter converter = (this.typeConverter != null ? this.typeConverter : new SimpleTypeConverter());
			value = converter.convertIfNecessary(rawValue, (Class<?>) type, TypeDescriptor.valueOf(type));
		}
		catch (TypeMismatchException ex) {
			bindingResult.pushNestedPath(name);
			bindingResult.rejectValue(rawValue, ex.getErrorCode(), "Failed to convert argument value");
			bindingResult.popNestedPath();
		}
		return (T) value;
	}


	/**
	 * BindingResult without a target Object, only for keeping track of errors
	 * and their associated, nested paths.
	 */
	@SuppressWarnings("serial")
	private static class ArgumentsBindingResult extends AbstractBindingResult {

		ArgumentsBindingResult(ResolvableType targetType) {
			super(initObjectName(targetType));
		}

		private static String initObjectName(ResolvableType targetType) {
			return (targetType.getSource() instanceof MethodParameter methodParameter ?
					Conventions.getVariableNameForParameter(methodParameter) :
					ClassUtils.getShortNameAsProperty(targetType.resolve(Object.class)));
		}

		@Override
		public Object getTarget() {
			return null;
		}

		@Override
		protected Object getActualFieldValue(String field) {
			return null;
		}

		public void rejectValue(@Nullable Object rawValue, String code, String defaultMessage) {
			addError(new FieldError(
					getObjectName(), fixedField(null), rawValue, true, resolveMessageCodes(code),
					null, defaultMessage));
		}
	}

}
