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

package org.springframework.graphql;

import okhttp3.mockwebserver.MockWebServer;
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
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        this.mockWebServer.shutdown();
    }

}
