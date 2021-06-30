package io.spring.sample.graphql.greeting;

import graphql.schema.idl.RuntimeWiring;

import org.springframework.graphql.boot.RuntimeWiringBuilderCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

@Component
public class GreetingDataWiring implements RuntimeWiringBuilderCustomizer {

	@Override
	public void customize(RuntimeWiring.Builder builder) {
		builder.type("Query", typeWiring -> typeWiring.dataFetcher("greeting", env -> {
			RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
			return "Hello " + attributes.getAttribute(RequestAttributeFilter.NAME_ATTRIBUTE, SCOPE_REQUEST);
		}));
	}

}
