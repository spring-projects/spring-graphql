/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql.data.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;

import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.util.TypeInformation;

/**
 * Utility to compute {@link PropertyPath property paths} from
 * a {@link DataFetchingFieldSelectionSet field selection} considering an underlying
 * Java type.
 * <p>
 * Property paths are created for each selected field that corresponds with a property
 * on the underlying type. Nested properties are represented with nested paths
 * if the nesting can be resolved to a concrete type, otherwise the nested path
 * is considered to be a composite property without further inspection.
 *
 * @author Mark Paluch
 * @since 1.0.0
 */
class PropertySelection {

	private final List<PropertyPath> propertyPaths;

	private PropertySelection(List<PropertyPath> propertyPaths) {
		this.propertyPaths = propertyPaths;
	}

	/**
	 * Create a property selection for the given {@link TypeInformation type} and
	 * {@link  DataFetchingFieldSelectionSet}.
	 *
	 * @param typeInformation the type to inspect
	 * @param selectionSet    the field selection to apply
	 * @return a property selection holding all selectable property paths.
	 */
	public static PropertySelection create(TypeInformation<?> typeInformation,
			DataFetchingFieldSelectionSet selectionSet) {
		return create(typeInformation, new DataFetchingFieldSelection(selectionSet));
	}

	private static PropertySelection create(TypeInformation<?> typeInformation, FieldSelection selection) {
		List<PropertyPath> propertyPaths = collectPropertyPaths(typeInformation,
				selection,
				path -> PropertyPath.from(path, typeInformation));
		return new PropertySelection(propertyPaths);
	}

	private static List<PropertyPath> collectPropertyPaths(TypeInformation<?> typeInformation,
			FieldSelection selection, Function<String, PropertyPath> propertyPathFactory) {
		List<PropertyPath> propertyPaths = new ArrayList<>();

		for (SelectedField selectedField : selection) {

			String propertyName = selectedField.getName();
			TypeInformation<?> property = typeInformation.getProperty(propertyName);

			if (property == null) {
				continue;
			}

			PropertyPath propertyPath = propertyPathFactory.apply(propertyName);
			FieldSelection nestedSelection = selection.select(selectedField);

			List<PropertyPath> pathsToAdd = Collections.singletonList(propertyPath);

			if (!nestedSelection.isEmpty() && property.getActualType() != null) {
				List<PropertyPath> nestedPaths = collectPropertyPaths(property.getRequiredActualType(),
						nestedSelection, propertyPath::nested);

				if (!nestedPaths.isEmpty()) {
					pathsToAdd = nestedPaths;
				}
			}

			propertyPaths.addAll(pathsToAdd);
		}

		return propertyPaths;
	}

	/**
	 * @return the property paths as list.
	 */
	public List<String> toList() {
		return this.propertyPaths.stream().map(PropertyPath::toDotPath)
				.collect(Collectors.toList());
	}


	enum EmptyFieldSelection implements FieldSelection {

		INSTANCE;

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public FieldSelection select(SelectedField field) {
			return INSTANCE;
		}

		@Override
		public Iterator<SelectedField> iterator() {
			return Collections.emptyIterator();
		}

	}


	/**
	 * Hierarchical representation of selected fields. Allows traversing the
	 * object graph with nested fields.
	 */
	interface FieldSelection extends Iterable<SelectedField> {

		/**
		 * @return {@code true} if the field selection is empty
		 */
		boolean isEmpty();

		/**
		 * Obtain the field selection (nested fields) for a given {@code field}.
		 *
		 * @param field the field for which nested fields should be obtained
		 * @return the field selection. Can be empty.
		 */
		FieldSelection select(SelectedField field);

	}


	static class DataFetchingFieldSelection implements FieldSelection {

		private final List<SelectedField> selectedFields;

		private final List<SelectedField> allFields;

		DataFetchingFieldSelection(DataFetchingFieldSelectionSet selectionSet) {
			this.selectedFields = selectionSet.getImmediateFields();
			this.allFields = selectionSet.getFields();
		}

		private DataFetchingFieldSelection(List<SelectedField> selectedFields,
				List<SelectedField> allFields) {
			this.selectedFields = selectedFields;
			this.allFields = allFields;
		}

		@Override
		public boolean isEmpty() {
			return selectedFields.isEmpty();
		}

		@Override
		public FieldSelection select(SelectedField field) {
			List<SelectedField> selectedFields = new ArrayList<>();

			for (SelectedField selectedField : allFields) {
				if (field.equals(selectedField.getParentField())) {
					selectedFields.add(selectedField);
				}
			}

			return (selectedFields.isEmpty() ? EmptyFieldSelection.INSTANCE
					: new DataFetchingFieldSelection(selectedFields, allFields));
		}

		@Override
		public Iterator<SelectedField> iterator() {
			return this.selectedFields.iterator();
		}

	}

}
