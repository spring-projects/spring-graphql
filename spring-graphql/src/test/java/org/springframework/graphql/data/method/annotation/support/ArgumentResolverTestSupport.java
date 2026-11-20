/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;


import java.lang.reflect.Method;
import java.util.Map;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.util.ClassUtils;

/**
 * Base class to test resolving {@code @Argument} and {@code @Arguments}
 * annotated method parameters.
 *
 * @author Rossen Stoyanchev
 */
class ArgumentResolverTestSupport {

	private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() { };


	private final ObjectMapper mapper = new ObjectMapper();


	protected MethodParameter methodParam(Class<?> controller, String methodName, Class<?>... parameters) {
		Method method = ClassUtils.getMethod(controller, methodName, parameters);
		MethodParameter methodParam = new SynthesizingMethodParameter(method, 0);
		methodParam.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
		return methodParam;
	}

	protected DataFetchingEnvironment environment(String argumentsJson) throws JacksonException {
		Map<String, Object> arguments = this.mapper.readValue(argumentsJson, MAP_TYPE_REFERENCE);
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build();
	}

	protected DataFetchingEnvironment environment(Map<String, Object> arguments) {
		return DataFetchingEnvironmentImpl.newDataFetchingEnvironment().arguments(arguments).build();
	}

}
