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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
					search: [SearchResult!]!
				}
				union SearchResult = Photo | Video
				type Photo {
					height: Int
					width: Int
				}
				type Video {
					title: String
				}
				""";


	@Nested
	class InterfaceFieldsNotOnJavaInterface {

		@Test
		void reportUnmappedFields() {
			SchemaReport report = inspectSchema(schema, SearchController.class);
			assertThatReport(report)
					.hasSkippedTypeCount(0)
					.hasUnmappedFieldCount(3)
					.containsUnmappedFields("Photo", "height", "width")
					.containsUnmappedFields("Video", "title");
		}


		sealed interface ResultItem permits Photo, Video { }
		record Photo() implements ResultItem { }
		record Video() implements ResultItem { }

		@Controller
		static class SearchController {

			@QueryMapping
			List<ResultItem> search() {
				throw new UnsupportedOperationException();
			}
		}
	}


	@Nested
	class GraphQlAndJavaTypeNameMismatch {

		@Test
		void useClassNameFunction() {

			SchemaReport report = inspectSchema(schema,
					initializer -> initializer.classNameFunction(type -> type.getName() + "Impl"),
					SearchController.class);

			assertThatReport(report)
					.hasSkippedTypeCount(0)
					.hasUnmappedFieldCount(3)
					.containsUnmappedFields("Photo", "height", "width")
					.containsUnmappedFields("Video", "title");
		}

		@Test
		void useClassNameTypeResolver() {

			ClassNameTypeResolver typeResolver = new ClassNameTypeResolver();
			typeResolver.addMapping(PhotoImpl.class, "Photo");

			SchemaReport report = inspectSchema(schema,
					initializer -> initializer.classResolver(ClassResolver.fromClassNameTypeResolver(typeResolver)),
					SearchController.class);

			assertThatReport(report)
					.hasUnmappedFieldCount(2).containsUnmappedFields("Photo", "height", "width")
					.hasSkippedTypeCount(1).containsSkippedTypes("Video");
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
			assertThatReport(report).hasSkippedTypeCount(2).containsSkippedTypes("Photo", "Video");
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

}
