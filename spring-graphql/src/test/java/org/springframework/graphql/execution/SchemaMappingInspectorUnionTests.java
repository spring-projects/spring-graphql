/*
 * Copyright 2020-2024 the original author or authors.
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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.SchemaMappingInspector.ClassResolver;
import org.springframework.stereotype.Controller;

/**
 * Tests for {@link SchemaMappingInspector} with union types.
 *
 * @author Rossen Stoyanchev
 */
public class SchemaMappingInspectorUnionTests extends SchemaMappingInspectorTestSupport {

	private static final String schema = """
				type Query {
					search: [SearchResult]
					article(id: ID): Article
				}
				union SearchResult = Article | Photo | Video
				type Photo {
					height: Int
					width: Int
				}
				type Video {
					title: String
				}
				type Article {
					content: String
				}
				""";


	@Nested
	class UnmappedFields {

		@Test
		void reportUnmappedFieldsByCheckingReturnTypePackage() {
			SchemaReport report = inspectSchema(schema, SearchController.class);
			assertThatReport(report)
					.hasSkippedTypeCount(1)
					.containsSkippedTypes("Article")
					.hasUnmappedFieldCount(4)
					.containsUnmappedFields("Query", "article")
					.containsUnmappedFields("Photo", "height", "width")
					.containsUnmappedFields("Video", "title");
		}

		@Test
		void reportUnmappedFieldsByCheckingControllerTypePackage() {
			SchemaReport report = inspectSchema(schema, ObjectSearchController.class);
			assertThatReport(report)
					.hasSkippedTypeCount(1)
					.containsSkippedTypes("Article")
					.hasUnmappedFieldCount(4)
					.containsUnmappedFields("Query", "article")
					.containsUnmappedFields("Photo", "height", "width")
					.containsUnmappedFields("Video", "title");
		}


		record Photo() implements ResultItem { }
		record Video() implements ResultItem { }

		sealed interface ResultItem permits Photo, Video { }

		@Controller
		static class SearchController {

			@QueryMapping
			List<ResultItem> search() {
				throw new UnsupportedOperationException();
			}
		}


		@Controller
		static class ObjectSearchController {

			@QueryMapping
			List<Object> search() {
				throw new UnsupportedOperationException();
			}
		}
	}


	@Nested
	class ClassNameAndClassResolver {

		@Test
		void classNameFunction() {

			SchemaReport report = inspectSchema(schema,
					initializer -> initializer.classNameFunction(type -> type.getName() + "Impl"),
					SearchController.class);

			assertThatReport(report)
					.hasSkippedTypeCount(1)
					.containsSkippedTypes("Article")
					.hasUnmappedFieldCount(4)
					.containsUnmappedFields("Query", "article")
					.containsUnmappedFields("Photo", "height", "width")
					.containsUnmappedFields("Video", "title");
		}

		@Test
		void classNameTypeResolver() {

			Map<Class<?>, String> mappings = Map.of(PhotoImpl.class, "Photo");

			SchemaReport report = inspectSchema(schema,
					initializer -> initializer.classResolver(ClassResolver.create(mappings)),
					SearchController.class);

			assertThatReport(report)
					.hasSkippedTypeCount(2)
					.containsSkippedTypes("Article", "Video")
					.hasUnmappedFieldCount(3)
					.containsUnmappedFields("Query", "article")
					.containsUnmappedFields("Photo", "height", "width");
		}

		sealed interface ResultItem permits PhotoImpl, VideoImpl { }
		record PhotoImpl() implements ResultItem { }
		record VideoImpl() implements ResultItem { }

		@Controller
		static class SearchController {

			@QueryMapping
			List<ResultItem> search() {
				throw new UnsupportedOperationException();
			}
		}
	}


	@Nested
	class SkippedTypes {

		@Test
		void reportSkippedImplementations() {
			SchemaReport report = inspectSchema(schema, SearchController.class);
			assertThatReport(report).hasSkippedTypeCount(3).containsSkippedTypes("Article", "Photo", "Video");
		}

		interface ResultItem { }

		@Controller
		static class SearchController {

			@QueryMapping
			List<ResultItem> search() {
				throw new UnsupportedOperationException();
			}
		}
	}


	@Nested
	class CandidateSkippedTypes {

		// A union member type is only a candidate to be skipped until the inspection is done.
		// Use of the concrete type elsewhere may provide more information.

		@Test
		void candidateNotSkippedIfConcreteUseElsewhere() {
			SchemaReport report = inspectSchema(schema, SearchController.class);
			assertThatReport(report).hasSkippedTypeCount(2).containsSkippedTypes("Photo", "Video");
		}

		@Controller
		static class SearchController {

			@QueryMapping
			List<Object> search() {
				throw new UnsupportedOperationException();
			}

			@QueryMapping
			Article article(@Argument Long id) {
				throw new UnsupportedOperationException();
			}
		}
	}


	/**
	 * Declared outside {@link CandidateSkippedTypes}, so the union lookup won't find it.
	 */
	private record Article(String content) { }

}
