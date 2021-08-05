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

package org.springframework.graphql.data.querydsl;

import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.TypeInformation;

/**
 * Lightweight {@link org.springframework.data.mapping.context.MappingContext}
 * to provide class metadata for entity to DTO mapping.
 *
 * @author Mark Paluch
 * @since 1.0.0
 */
class DtoMappingContext extends AbstractMappingContext<DtoMappingContext.DtoPersistentEntity<?>,
		DtoMappingContext.DtoPersistentProperty> {

	@Override
	protected boolean shouldCreatePersistentEntityFor(TypeInformation<?> type) {
		// No Java std lib type introspection to not interfere with encapsulation.
		// We do not want to get into the business of materializing Java types.
		if (type.getType().getName().startsWith("java.") || type.getType().getName().startsWith("javax.")) {
			return false;
		}
		return super.shouldCreatePersistentEntityFor(type);
	}

	@Override
	protected <T> DtoPersistentEntity<?> createPersistentEntity(TypeInformation<T> typeInformation) {
		return new DtoPersistentEntity<>(typeInformation);
	}

	@Override
	protected DtoPersistentProperty createPersistentProperty(
			Property property, DtoPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {

		return new DtoPersistentProperty(property, owner, simpleTypeHolder);
	}

	static class DtoPersistentEntity<T> extends BasicPersistentEntity<T, DtoPersistentProperty> {

		public DtoPersistentEntity(TypeInformation<T> information) {
			super(information);
		}

	}

	static class DtoPersistentProperty extends AnnotationBasedPersistentProperty<DtoPersistentProperty> {

		public DtoPersistentProperty(
				Property property, PersistentEntity<?, DtoPersistentProperty> owner,
				SimpleTypeHolder simpleTypeHolder) {

			super(property, owner, simpleTypeHolder);
		}

		@Override
		protected Association<DtoPersistentProperty> createAssociation() {
			return null;
		}

	}

}
