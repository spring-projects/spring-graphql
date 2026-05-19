/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.graphql.server.webflux;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.selenium.BrowserWebDriverContainer;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.graphql.BookSource;
import org.springframework.graphql.GraphQlSetup;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.Testcontainers.exposeHostPorts;

@Testcontainers(disabledWithoutDocker = true)
class GraphiQlBrowserTests {

	private BrowserWebDriverContainer browser;

	private DisposableServer server;

	@BeforeEach
	void startServer() {
		GraphiQlHandler graphiQlHandler = new GraphiQlHandler("/graphql", null);
		GraphQlHttpHandler graphqlHandler = GraphQlSetup.schemaResource(BookSource.schema)
				.queryFetcher("bookById", env -> BookSource.getBook(Long.parseLong(env.getArgument("id"))))
				.toHttpHandlerWebFlux();

		RouterFunction<ServerResponse> router = RouterFunctions.route()
				.GET("/", req -> ServerResponse.ok().build())
				.GET("/graphiql", graphiQlHandler::handleRequest)
				.POST("/graphql", graphqlHandler::handleRequest)
				.build();

		HttpHandler httpHandler = RouterFunctions.toHttpHandler(router);
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		this.server = HttpServer.create().port(0).handle(adapter).bindNow();

		exposeHostPorts(this.server.port());

		this.browser = new BrowserWebDriverContainer("selenium/standalone-chrome");
		this.browser.start();
	}

	@AfterEach
	void stopServer() {
		if (this.browser != null) {
			this.browser.stop();
		}
		if (this.server != null) {
			this.server.disposeNow();
		}
	}

	@Test
	void pageRendersSuccessfully() {
		RemoteWebDriver driver = getWebDriver();
		driver.get(getUrl("/graphiql"));

		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		wait.until(ExpectedConditions.presenceOfElementLocated(By.className("graphiql-container")));
		//wait.until(ExpectedConditions.presenceOfElementLocated(By.className("graphiql-logo")));

		assertThat(driver.getTitle()).as("check the page title").isEqualTo("GraphiQL");
		driver.quit();
	}

	@Test
	void executeQuery() {
		RemoteWebDriver driver = getWebDriver();
		driver.get(getUrl("/graphiql"));
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		wait.until(ExpectedConditions.presenceOfElementLocated(By.className("graphiql-container")));

		sendGraphQlRequest("{bookById(id:\"1\"){name}}", wait);
		wait.until(ExpectedConditions.textToBePresentInElementLocated(By.className("graphiql-container"), "Nineteen Eighty-Four"));

		driver.quit();
	}

	@Test
	void supportsXsrfCookie() {
		RemoteWebDriver driver = getWebDriver();
		driver.get(getUrl("/"));
		driver.manage().addCookie(new Cookie("XSRF-TOKEN", "spring-cookie"));
		driver.get(getUrl("/graphiql"));
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

		wait.until(ExpectedConditions.textToBePresentInElementLocated(By.className("graphiql-container"), "{ \"X-XSRF-TOKEN\" : \"spring-cookie\" }"));
		sendGraphQlRequest("{bookById(id:\"1\"){name}}", wait);
		wait.until(ExpectedConditions.textToBePresentInElementLocated(By.className("graphiql-container"), "Nineteen Eighty-Four"));

		driver.quit();
	}

	private static void sendGraphQlRequest(String request, WebDriverWait wait) {
		WebElement queryEditor = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".graphiql-query-editor .inputarea")));
		queryEditor.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
		queryEditor.sendKeys(request);
		wait.until(ExpectedConditions.elementToBeClickable(By.className("graphiql-execute-button"))).click();
	}

	private String getUrl(String path) {
		return "http://host.testcontainers.internal:" + this.server.port() + path;
	}

	private RemoteWebDriver getWebDriver() {
		return new RemoteWebDriver(this.browser.getSeleniumAddress(), new ChromeOptions());
	}
}
