/*
 * Copyright 2002-2024 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.graphql.ResponseHelper;
import org.springframework.graphql.TestExecutionGraphQlService;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumSchemaMappingTests {

    private static final String schema = """
            type Query {
            	animals: [Animal!]!
            }
            type Animal {
            	id: ID!
            	name: String!
            	classification: Classification!
            }
            type Classification {
            	id: ID!
            	name: String!
            }
            """;

    private static final List<Animal> animalList = new ArrayList<>();


    static {
        animalList.add(new Animal("1", "Penguin", Classification.Bird));
        animalList.add(new Animal("2", "Dog", Classification.Mammal));
        animalList.add(new Animal("3", "Shark", Classification.Fish));
    }

	private final ClassificationController classificationController = new ClassificationController();


    @Test
    void resolveFromInterfaceHierarchyWithClassNames() {

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(ClassificationController.class, () -> classificationController);
        context.refresh();

        GraphQlSetup graphQlSetup =
                GraphQlSetup.schemaContent(schema).runtimeWiringForAnnotatedControllers(context);


        String document = """
                query Animals {
                	animals {
                		__typename
                		name
                		classification {
                			id
                		}
                	}
                }
                """;



        TestExecutionGraphQlService service =
                graphQlSetup.queryFetcher("animals", env -> animalList).toGraphQlService();

        ResponseHelper response = ResponseHelper.forResponse(service.execute(document));
        assertThat(response.errorCount()).isEqualTo(0);


        String classificationIdBird = response.rawValue("animals[0].classification.id");
        assertThat(classificationIdBird).isEqualTo(Classification.Bird.ordinal() + "-" + Classification.Bird.name());

        String classificationIdMammal = response.rawValue("animals[1].classification.id");
        assertThat(classificationIdMammal).isEqualTo(Classification.Mammal.ordinal() + "-" + Classification.Mammal.name());

        String classificationIdFish = response.rawValue("animals[2].classification.id");
        assertThat(classificationIdFish).isEqualTo(Classification.Fish.ordinal() + "-" + Classification.Fish.name());

    }



    record Animal(String id, String name, Classification classification) {}

    enum Classification {
        Bird,
        Fish,
        Mammal
    }

    @Controller
    @SchemaMapping(typeName = "Classification")
    static class ClassificationController {

        @SchemaMapping
        public String id(Classification classification) {
            return classification.ordinal() + "-" + classification.name();
        }

    }


}
