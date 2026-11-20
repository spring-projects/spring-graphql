/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.docs.graalvm.client;

import java.util.List;

public record Project(String slug, String name, List<Releases> releases) {
}
