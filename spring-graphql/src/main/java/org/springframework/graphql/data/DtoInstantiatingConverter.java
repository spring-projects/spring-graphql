/*
 * Copyright 2002-2021 the original author or authors.
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

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.PreferredConstructor;
import org.springframework.data.mapping.PreferredConstructor.Parameter;
import org.springframework.data.mapping.SimplePropertyHandler;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.EntityInstantiator;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.data.mapping.model.ParameterValueProvider;

/**
 * {@link Converter} to instantiate DTOs from fully equipped domain objects.
 *
 * @author Mark Paluch
 * @since 1.0.0
 */
class DtoInstantiatingConverter<T> implements Converter<Object, T> {

	private final Class<T> targetType;

	private final MappingContext<? extends PersistentEntity<?, ?>, ? extends PersistentProperty<?>> context;

	private final EntityInstantiator instantiator;

	/**
	 * Create a new {@link Converter} to instantiate DTOs.
	 * @param dtoType target type
	 * @param context mapping context to be used
	 * @param entityInstantiators the instantiators to use for object creation
	 */
	public DtoInstantiatingConverter(Class<T> dtoType,
			MappingContext<? extends PersistentEntity<?, ?>, ? extends PersistentProperty<?>> context,
			EntityInstantiators entityInstantiators) {

		this.targetType = dtoType;
		this.context = context;
		this.instantiator = entityInstantiators.getInstantiatorFor(context.getRequiredPersistentEntity(dtoType));
	}

	@SuppressWarnings("unchecked")
	@Override
	public T convert(Object source) {

		if (targetType.isInterface()) {
			return (T) source;
		}

		PersistentEntity<?, ?> sourceEntity = this.context.getRequiredPersistentEntity(source.getClass());

		PersistentPropertyAccessor<?> sourceAccessor = sourceEntity.getPropertyAccessor(source);
		PersistentEntity<?, ?> entity = this.context.getRequiredPersistentEntity(this.targetType);
		PreferredConstructor<?, ? extends PersistentProperty<?>> constructor = entity.getPersistenceConstructor();

		@SuppressWarnings({"rawtypes", "unchecked"})
		Object dto = this.instantiator.createInstance(entity, new ParameterValueProvider() {

					@Override
					public Object getParameterValue(Parameter parameter) {
						return sourceAccessor.getProperty(
								sourceEntity.getRequiredPersistentProperty(parameter.getName()));
					}
				});

		PersistentPropertyAccessor<?> dtoAccessor = entity.getPropertyAccessor(dto);

		entity.doWithProperties((SimplePropertyHandler) property -> {

			if (constructor.isConstructorParameter(property)) {
				return;
			}

			dtoAccessor.setProperty(property,
					sourceAccessor.getProperty(sourceEntity.getRequiredPersistentProperty(property.getName())));
		});

		return (T) dto;
	}

}
