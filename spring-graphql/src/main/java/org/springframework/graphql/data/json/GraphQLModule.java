/*
 * Copyright 2002-2025 the original author or authors.
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

package org.springframework.graphql.data.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import org.springframework.graphql.data.ArgumentValue;

public class GraphQLModule extends Module {

	@Override
	public String getModuleName() {
		return GraphQLModule.class.getName();
	}

	@Override
	public Version version() {
		return Version.unknownVersion();
	}

	@Override
	public void setupModule(final SetupContext context) {
		context.addSerializers(new ArgumentValueSerializers());
		context.addDeserializers(new ArgumentValueDeserializers());
		context.addTypeModifier(new ArgumentValueTypeModifier());

		context.configOverride(ArgumentValue.class)
			.setInclude(JsonInclude.Value.empty().withValueInclusion(JsonInclude.Include.NON_ABSENT));
	}

}
