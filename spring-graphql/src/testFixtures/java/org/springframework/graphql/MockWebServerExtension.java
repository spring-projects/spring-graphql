/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql;

import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 Extension that creates, starts and stops a {@link MockWebServer} instance for each test
 * and injects it as a method parameter in test methods.
 *
 * <p>Test classes should be annotated with {@code ExtendWith(MockWebServerExtension.class)}
 * to use this extension.
 * @author Brian Clozel
 */
public class MockWebServerExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

	private MockWebServer mockWebServer;

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		return parameterContext.getParameter().getType()
				.equals(MockWebServer.class);
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		return this.mockWebServer;
	}

	@Override
	public void beforeEach(ExtensionContext extensionContext) throws Exception {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();
	}

	@Override
	public void afterEach(ExtensionContext extensionContext) {
		this.mockWebServer.close();
	}

}
