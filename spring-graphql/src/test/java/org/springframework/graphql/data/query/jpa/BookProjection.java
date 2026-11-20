/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.query.jpa;

import org.springframework.beans.factory.annotation.Value;

interface BookProjection {

	@Value("#{target.name + ' by ' + target.author.firstName + ' ' + target.author.lastName}")
	String getName();

}
