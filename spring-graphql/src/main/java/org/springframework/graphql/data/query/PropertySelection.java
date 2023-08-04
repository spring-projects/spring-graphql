/*
 * Copyright 2002-2023 the original author or authors.
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

import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.SelectedField;

import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.CollectionUtils;

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
final class PropertySelection {

	private final List<PropertyPath> propertyPaths;


	private PropertySelection(List<PropertyPath> propertyPaths) {
		this.propertyPaths = propertyPaths;
	}


	/**
	 * @return the property paths as list.
	 */
	public List<String> toList() {
		return this.propertyPaths.stream().map(PropertyPath::toDotPath).toList();
	}


	/**
	 * Create a property selection for the given {@link TypeInformation type} and
	 * {@link  DataFetchingFieldSelectionSet}.
	 *
	 * @param typeInfo the type to inspect
	 * @param selectionSet    the field selection to apply
	 * @return a property selection holding all selectable property paths.
	 */
	public static PropertySelection create(TypeInformation<?> typeInfo, DataFetchingFieldSelectionSet selectionSet) {
		FieldSelection selection = new DataFetchingFieldSelection(selectionSet);
		List<PropertyPath> paths = getPropertyPaths(typeInfo, selection, path -> PropertyPath.from(path, typeInfo));
		return new PropertySelection(paths);
	}

	private static List<PropertyPath> getPropertyPaths(
			TypeInformation<?> typeInfo, FieldSelection selection, Function<String, PropertyPath> pathFactory) {

		List<PropertyPath> result = new ArrayList<>();

		for (SelectedField selectedField : selection) {
			String propertyName = selectedField.getName();
			TypeInformation<?> propertyTypeInfo = typeInfo.getProperty(propertyName);
			if (propertyTypeInfo == null) {
				if (isConnectionEdges(selectedField)) {
					getConnectionPropertyPaths(typeInfo, selection, pathFactory, selectedField, result);
				}
				else if (isConnectionEdgeNode(selectedField)) {
					getConnectionPropertyPaths(typeInfo, selection, pathFactory, selectedField, result);
				}
				continue;
			}

			PropertyPath propertyPath = pathFactory.apply(propertyName);

			List<PropertyPath> nestedPaths = null;
			FieldSelection nestedSelection = selection.select(selectedField);
			if (!nestedSelection.isEmpty() && propertyTypeInfo.getActualType() != null) {
				TypeInformation<?> actualType = propertyTypeInfo.getRequiredActualType();
				nestedPaths = getPropertyPaths(actualType, nestedSelection, propertyPath::nested);
			}

			result.addAll(CollectionUtils.isEmpty(nestedPaths) ?
					Collections.singletonList(propertyPath) : nestedPaths);
		}

		return result;
	}

	private static boolean isConnectionEdges(SelectedField selectedField) {
		return "edges".equals(selectedField.getName()) &&
			   selectedField.getParentField().getType() instanceof GraphQLNamedOutputType namedType &&
			   namedType.getName().endsWith("Connection");
	}

	private static boolean isConnectionEdgeNode(SelectedField selectedField) {
		return "node".equals(selectedField.getName()) && isConnectionEdges(selectedField.getParentField());
	}

	private static void getConnectionPropertyPaths(
			TypeInformation<?> typeInfo, FieldSelection selection, Function<String, PropertyPath> pathFactory,
			SelectedField selectedField, List<PropertyPath> result) {

		FieldSelection nestedSelection = selection.select(selectedField);
		if (!nestedSelection.isEmpty()) {
			TypeInformation<?> actualType = typeInfo.getRequiredActualType();
			List<PropertyPath> paths = getPropertyPaths(actualType, nestedSelection, pathFactory);
			if (!CollectionUtils.isEmpty(paths)) {
				result.addAll(paths);
			}
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


	private static class DataFetchingFieldSelection implements FieldSelection {

		private final List<SelectedField> selectedFields;

		private final List<SelectedField> allFields;

		DataFetchingFieldSelection(DataFetchingFieldSelectionSet selectionSet) {
			this.selectedFields = selectionSet.getImmediateFields();
			this.allFields = selectionSet.getFields();
		}

		private DataFetchingFieldSelection(List<SelectedField> selectedFields, List<SelectedField> allFields) {
			this.selectedFields = selectedFields;
			this.allFields = allFields;
		}

		@Override
		public boolean isEmpty() {
			return selectedFields.isEmpty();
		}

		@Override
		public FieldSelection select(SelectedField field) {
			List<SelectedField> selectedFields = null;

			for (SelectedField selectedField : this.allFields) {
				if (field.equals(selectedField.getParentField())) {
					selectedFields = selectedFields != null ? selectedFields : new ArrayList<>();
					selectedFields.add(selectedField);
				}
			}

			return selectedFields != null ?
					new DataFetchingFieldSelection(selectedFields, this.allFields) :
					EmptyFieldSelection.INSTANCE;
		}

		@Override
		public Iterator<SelectedField> iterator() {
			return this.selectedFields.iterator();
		}

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

}
