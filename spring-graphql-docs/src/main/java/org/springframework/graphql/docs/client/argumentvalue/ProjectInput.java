/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.client.argumentvalue;


import org.springframework.graphql.data.ArgumentValue;

public record ProjectInput(String id, ArgumentValue<String> name) {

}
