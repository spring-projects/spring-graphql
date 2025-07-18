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

package org.springframework.graphql.test.tester;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.spi.json.AbstractJsonProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * {@link com.jayway.jsonpath.spi.json.JsonProvider} for Jackson 3.x.
 * @author Brian Clozel
 */
class JacksonJsonProvider extends AbstractJsonProvider {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final ObjectReader objectReader = this.objectMapper.reader();

	@Override
	public Object parse(String json) throws InvalidJsonException {
		try {
			return this.objectReader.readValue(json);
		}
		catch (JacksonException exc) {
			throw new InvalidJsonException(exc, json);
		}
	}

	@Override
	public Object parse(InputStream jsonStream, String charset) throws InvalidJsonException {
		try {
			return this.objectReader.readValue(new InputStreamReader(jsonStream, charset));
		}
		catch (IOException exc) {
			throw new InvalidJsonException(exc);
		}
	}

	@Override
	public String toJson(Object obj) {
		StringWriter writer = new StringWriter();
		try {
			JsonGenerator generator = this.objectMapper.createGenerator(writer);
			this.objectMapper.writeValue(generator, obj);
			writer.flush();
			writer.close();
			generator.close();
			return writer.getBuffer().toString();
		}
		catch (IOException exc) {
			throw new InvalidJsonException(exc);
		}
	}

	@Override
	public Object createArray() {
		return new LinkedList<Object>();
	}

	@Override
	public Object createMap() {
		return new LinkedHashMap<String, Object>();
	}
}
