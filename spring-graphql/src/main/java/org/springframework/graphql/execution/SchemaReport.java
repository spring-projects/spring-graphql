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

package org.springframework.graphql.execution;

import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Map;

import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.jspecify.annotations.Nullable;

import org.springframework.core.Nullness;
import org.springframework.util.MultiValueMap;

/**
 * Report produced as a result of inspecting schema mappings.
 *
 * <p>The inspection checks if schema fields are covered either by a
 * {@link DataFetcher} registration, or match a Java object property. Fields
 * that have neither are reported as {@link #unmappedFields()}.
 * The inspection also checks if any {@code DataFetcher} are registered against
 * schema fields that don't exist and reports those as {@link #unmappedRegistrations()}.
 *
 * @author Rossen Stoyanchev
 * @since 1.2.0
 */
public interface SchemaReport {

	/**
	 * Return the inspected schema with type and field definitions.
	 */
	GraphQLSchema schema();

	/**
	 * Return the coordinates of unmapped fields. Such fields have neither a
	 * {@link DataFetcher} registration, such as a {@code @SchemaMapping}
	 * method, nor a matching Java property in the return type from the parent
	 * {@code DataFetcher}.
	 */
	List<FieldCoordinates> unmappedFields();

	/**
	 * Return the coordinates for invalid {@link DataFetcher} registrations
	 * referring to fields that don't exist in the schema.
	 */
	Map<FieldCoordinates, DataFetcher<?>> unmappedRegistrations();

	/**
	 * Return a map with {@link DataFetcher}s and the names of arguments they
	 * depend on that don't exist.
	 * @since 1.3.0
	 */
	MultiValueMap<DataFetcher<?>, String> unmappedArguments();

	/**
	 * Return the coordinates for nullness errors between
	 * schema fields and the corresponding {@link AnnotatedElement} in the application.
	 * @see NullnessError
	 */
	Map<FieldCoordinates, NullnessError> fieldNullnessErrors();

	/**
	 * Return a map with {@link DataFetcher}s and information for its arguments
	 * if there is a nullness error between the schema arguments and the
	 * corresponding {@link AnnotatedElement} in the application.
	 * @see NullnessError
	 */
	MultiValueMap<DataFetcher<?>, NullnessError> argumentNullnessErrors();

	/**
	 * Return types skipped during the inspection, either because the schema type
	 * is not supported, e.g. union, or because there is insufficient Java type
	 * information, e.g. controller method that returns {@code Object} or wrapper
	 * type (collection, reactive, asynchronous) with wildcard generics.
	 */
	List<SkippedType> skippedTypes();

	/**
	 * Return the {@code DataFetcher} for the given field coordinates, if registered.
	 * @param coordinates the field coordinates
	 */
	@Nullable DataFetcher<?> dataFetcher(FieldCoordinates coordinates);


	/**
	 * Information about a schema type skipped during the inspection.
	 */
	interface SkippedType {

		/**
		 * Return the type that was skipped. This corresponds to the output type
		 * of the {@link #fieldCoordinates() field} where the type was
		 * encountered, possibly with {@link NonNullType} and {@link ListType}
		 * wrapper types removed.
		 */
		GraphQLType type();

		/**
		 * Return the coordinates of the field where the type was encountered.
		 */
		FieldCoordinates fieldCoordinates();

	}

	/**
	 * Information about a Nullness error between the GraphQL schema and the application code.
	 *
	 * <p>Errors are raised when the application nullness information breaks requirements
	 * from the schema: a schema field is marked as "non-null" (like {@code "id: ID!"}),
	 * but the application element is "nullable" (like {@code @Nullable String getId();}).
	 *
	 * <p>The opposite ({@code "author: Author"} and {@code @NonNull Author getAuthor();}
	 * is not considered as an error, because if an exception is raised while fetching a
	 * field, the GraphQL engine will null out fields in the hierarchy up until {@code null}
	 * is allowed. A schema consistently using "non-null" types will not return partial responses.
	 */
	interface NullnessError {

		/**
		 * Nullness expected in the schema.
		 */
		Nullness schemaNullness();

		/**
		 * Nullness in the application code.
		 */
		Nullness applicationNullness();

		/**
		 * The annotated element implementing the considered part of the schema.
		 */
		AnnotatedElement annotatedElement();

	}

}
