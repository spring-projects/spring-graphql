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
	@Nullable
	public Object bind(
			DataFetchingEnvironment environment, @Nullable String name, ResolvableType targetType)
			throws BindException {

		Object rawValue = (name != null ? environment.getArgument(name) : environment.getArguments());
		boolean isOmitted = (name != null && !environment.getArguments().containsKey(name));

		ArgumentsBindingResult bindingResult = new ArgumentsBindingResult(targetType);

		Object value = bindRawValue(
				"$", rawValue, isOmitted, targetType, targetType.resolve(Object.class), bindingResult);

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
	@Nullable
	private Object bindRawValue(
			String name, @Nullable Object rawValue, boolean isOmitted,
			ResolvableType targetType, Class<?> targetClass, ArgumentsBindingResult bindingResult) {

		boolean isOptional = (targetClass == Optional.class);
		boolean isArgumentValue = (targetClass == ArgumentValue.class);

		if (isOptional || isArgumentValue) {
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

	@Nullable
	private Object bindMap(
			String name, Map<String, Object> rawMap, ResolvableType targetType, Class<?> targetClass,
			ArgumentsBindingResult bindingResult) {

		if (Map.class.isAssignableFrom(targetClass)) {
			return bindMapToMap(name, rawMap, targetType, targetClass, bindingResult);
		}

		bindingResult.pushNestedPath(name);

		Constructor<?> constructor = BeanUtils.getResolvableConstructor(targetClass);

		Object value = (constructor.getParameterCount() > 0 ?
				bindMapToObjectViaConstructor(rawMap, constructor, targetType, bindingResult) :
				bindMapToObjectViaSetters(rawMap, constructor, targetType, bindingResult));

		bindingResult.popNestedPath();

		return value;
	}

	private Map<?, Object> bindMapToMap(
			String name, Map<String, Object> rawMap, ResolvableType targetType, Class<?> targetClass,
			ArgumentsBindingResult bindingResult) {

		ResolvableType valueType = targetType.asMap().getGeneric(1);
		Class<?> valueClass = valueType.resolve();
		if (valueClass == null) {
			bindingResult.rejectArgumentValue(name, null, "unknownType", "Unknown Map value type");
			return Collections.emptyMap(); // Keep going, to record more errors
		}

		Map<String, Object> map = CollectionFactory.createMap(targetClass, rawMap.size());

		for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
			String key = entry.getKey();
			String indexedName = name + "[" + key + "]";
			map.put(key, bindRawValue(indexedName, entry.getValue(), false, valueType, valueClass, bindingResult));
		}

		return map;
	}

	@Nullable
	private Object bindMapToObjectViaConstructor(
			Map<String, Object> rawMap, Constructor<?> constructor, ResolvableType ownerType,
			ArgumentsBindingResult bindingResult) {

		String[] paramNames = BeanUtils.getParameterNames(constructor);
		Class<?>[] paramTypes = constructor.getParameterTypes();
		Object[] constructorArguments = new Object[paramTypes.length];

		for (int i = 0; i < paramNames.length; i++) {
			String name = paramNames[i];

			ResolvableType targetType = ResolvableType.forType(
					ResolvableType.forConstructorParameter(constructor, i).getType(), ownerType);

			constructorArguments[i] = bindRawValue(
					name, rawMap.get(name), !rawMap.containsKey(name), targetType, paramTypes[i], bindingResult);
		}

		try {
			return BeanUtils.instantiateClass(constructor, constructorArguments);
		}
		catch (BeanInstantiationException ex) {
			// Ignore, if we had binding errors to begin with
			if (bindingResult.hasErrors()) {
				return null;
			}
			throw ex;
		}
	}

	private Object bindMapToObjectViaSetters(
			Map<String, Object> rawMap, Constructor<?> constructor, ResolvableType ownerType,
			ArgumentsBindingResult bindingResult) {

		Object target = BeanUtils.instantiateClass(constructor);
		BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(target);

		for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
			String key = entry.getKey();
			TypeDescriptor typeDescriptor = beanWrapper.getPropertyTypeDescriptor(key);
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

		return target;
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private <T> T convertValue(
			String name, @Nullable Object rawValue, ResolvableType type, Class<T> clazz,
			ArgumentsBindingResult bindingResult) {

		Object value = null;
		try {
			TypeConverter converter =
					(this.typeConverter != null ? this.typeConverter : new SimpleTypeConverter());

			value = converter.convertIfNecessary(
					rawValue, (Class<?>) clazz, new TypeDescriptor(type, null, null));
		}
		catch (TypeMismatchException ex) {
			bindingResult.rejectArgumentValue(name, rawValue, ex.getErrorCode(), "Failed to convert argument value");
		}

		return (T) value;
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

		public void rejectArgumentValue(
				String field, @Nullable Object rawValue, String code, String defaultMessage) {

			addError(new FieldError(
					getObjectName(), fixedField(field), rawValue, true, resolveMessageCodes(code),
					null, defaultMessage));
		}
	}

}
