/*
 * Copyright 2002-2022 the original author or authors.
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
package org.springframework.graphql.data.method.annotation.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Mono;
import reactor.test.publisher.TestPublisher;
import reactor.util.context.Context;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.function.Function;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Tests for {@link AuthenticationPrincipalArgumentResolver}.
 *
 * @author Rob Winch
 */
class AuthenticationPrincipalArgumentResolverTests {

	private final static Class<?> MONO_USER_DETAILS_CLASS = ResolvableType.forClassWithGenerics(Mono.class, UserDetails.class).getRawClass();

	private final static Class<UserDetails> USER_DETAILS_CLASS = UserDetails.class;

	private final static Class<String> STRING_CLASS = String.class;

	private final static Class<?> MONO_STRING_CLASS = ResolvableType.forClassWithGenerics(Mono.class, String.class).getRawClass();

	private final static Class<?> PUBLISHER_USER_DETAILS_CLASS = ResolvableType.forClassWithGenerics(Publisher.class, UserDetails.class).getRawClass();

	private final static Class<?> TESTPUBLISHER_USER_DETAILS_CLASS = ResolvableType.forClassWithGenerics(TestPublisher.class, UserDetails.class).getRawClass();

	private AuthenticationPrincipalArgumentResolver resolver;

	@BeforeEach
	void setup() {
		this.resolver = new AuthenticationPrincipalArgumentResolver((beanName, context) -> new PrincipalConverter());
	}

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void supportsParameterWhenNoAnnotation() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "noParameter", USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(methodParameter)).isFalse();
	}

	@Test
	void supportsParameterWhenWrongAnnotation() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "argument", USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(methodParameter)).isFalse();
	}

	@Test
	void supportsParameterWhenCurrentUser() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "currentUser", USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(methodParameter)).isTrue();
	}

	@Test
	void supportsParameterWhenAuthenticationPrincipal() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "userDetails", USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(methodParameter)).isTrue();
	}

	@Test
	void resolveArgumentWhenAuthenticationPrincipal() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "userDetails", USER_DETAILS_CLASS);
		Mono<UserDetails> userDetails = (Mono<UserDetails>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenCurrentUser() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "currentUser", USER_DETAILS_CLASS);
		Mono<UserDetails> userDetails = (Mono<UserDetails>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenNoSecurityContext() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "currentUser", USER_DETAILS_CLASS);
		Mono<UserDetails> userDetails = (Mono<UserDetails>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.block()).isNull();
	}

	@Test
	void resolveArgumentWhenSecurityContextHolder() throws Exception {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(usernamePasswordAuthentication());
		SecurityContextHolder.setContext(context);
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "currentUser", USER_DETAILS_CLASS);
		Mono<UserDetails> userDetails = (Mono<UserDetails>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenAuthenticationPrincipalUsername() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "username", STRING_CLASS);
		Mono<String> userDetails = (Mono<String>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.contextWrite(authenticationContext()).block()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenBeanName() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "beanName", USER_DETAILS_CLASS);
		Mono<UserDetails> userDetails = (Mono<UserDetails>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenErrorOnInvalidType() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "errorOnInvalidType", String.class);
		Mono<Object> userDetails = (Mono<Object>) this.resolver.resolveArgument(methodParameter, null);
		assertThatExceptionOfType(ClassCastException.class)
				.isThrownBy(() -> userDetails.contextWrite(authenticationContext()).block());
	}

	@Test
	void resolveArgumentWhenInvalidType() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "invalidType", STRING_CLASS);
		Mono<Mono<Object>> userDetails = (Mono<Mono<Object>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.flatMap(u -> u).contextWrite(authenticationContext()).block()).isNull();
	}

	@Test
	void supportsParameterWhenMonoNoAnnotation() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "noParameter", MONO_USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(methodParameter)).isFalse();
	}

	@Test
	void supportsParameterWhenMonoWrongAnnotation() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "argument", MONO_USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(methodParameter)).isFalse();
	}

	@Test
	void supportsParameterWhenMonoCurrentUser() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "currentUser", MONO_USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(methodParameter)).isTrue();
	}

	@Test
	void supportsParameterWhenMonoAuthenticationPrincipal() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "userDetails", MONO_USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(methodParameter)).isTrue();
	}

	@Test
	void resolveArgumentWhenMonoAuthenticationPrincipal() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "userDetails", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> userDetails = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.block().contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoCurrentUser() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "currentUser", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> userDetails = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.block().contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoNoSecurityContext() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "currentUser", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> userDetails = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.block().block()).isNull();
	}

	@Test
	void resolveArgumentWhenMonoSecurityContextHolder() throws Exception {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(usernamePasswordAuthentication());
		SecurityContextHolder.setContext(context);
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "currentUser", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> userDetails = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.block().contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoAuthenticationPrincipalUsername() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "username", MONO_STRING_CLASS);
		Mono<Mono<String>> userDetails = (Mono<Mono<String>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.block().contextWrite(authenticationContext()).block()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoBeanName() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "beanName", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> userDetails = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.flatMap(u -> u).contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoErrorOnInvalidType() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "errorOnInvalidType", MONO_STRING_CLASS);
		Mono<Mono<Object>> userDetails = (Mono<Mono<Object>>) this.resolver.resolveArgument(methodParameter, null);
		assertThatExceptionOfType(ClassCastException.class)
				.isThrownBy(() -> userDetails.flatMap(u -> u).contextWrite(authenticationContext()).block());
	}

	@Test
	void resolveArgumentWhenMonoInvalidType() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "invalidType", MONO_STRING_CLASS);
		Mono<Mono<Object>> userDetails = (Mono<Mono<Object>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.flatMap(u -> u).contextWrite(authenticationContext()).block()).isNull();
	}

	@Test
	void resolveArgumentWhenPublisher() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "publisher", PUBLISHER_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> userDetails = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.block().contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenTestPublisherThenEmptyMono() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "publisher", TESTPUBLISHER_USER_DETAILS_CLASS);
		Mono<TestPublisher<UserDetails>> userDetails = (Mono<TestPublisher<UserDetails>>) this.resolver.resolveArgument(methodParameter, null);
		assertThat(userDetails.contextWrite(authenticationContext()).block()).isNull();
	}

	@Test
	void resolveArgumentWhenTestPublisherAndErrorOnInvalidType() throws Exception {
		MethodParameter methodParameter = firstMethodParameter(UserController.class, "errorOnInvalidType", TESTPUBLISHER_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> userDetails = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(methodParameter, null);
		assertThatExceptionOfType(ClassCastException.class)
				.isThrownBy(() -> userDetails.flatMap(u -> u).contextWrite(authenticationContext()).block());
	}

	private MethodParameter firstMethodParameter(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		Method method = ClassUtils.getMethod(clazz, methodName, paramTypes);
		return methodParam(method, 0);
	}

	private MethodParameter methodParam(Method method, int index) {
		MethodParameter methodParameter = new SynthesizingMethodParameter(method, index);
		methodParameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
		return methodParameter;
	}

	private static Function<Context, Context> authenticationContext() {
		return (context) -> ReactiveSecurityContextHolder.withAuthentication(usernamePasswordAuthentication());
	}

	private static Authentication usernamePasswordAuthentication() {
		UserDetails userDetails = userDetails();
		return new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(), userDetails.getAuthorities());
	}

	private static UserDetails userDetails() {
		return new User("user", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
	}

	static class PrincipalConverter {
		public UserDetails convert(UserDetails userDetails) {
			return userDetails;
		}
	}

	@Controller
	static class UserController {

		@QueryMapping
		public UserDetails noParameter(UserDetails userDetails) {
			return userDetails;
		}

		@QueryMapping
		public UserDetails argument(@Argument UserDetails userDetails) {
			return userDetails;
		}

		@QueryMapping
		public UserDetails userDetails(@AuthenticationPrincipal UserDetails userDetails) {
			return userDetails;
		}

		@QueryMapping
		public UserDetails currentUser(@CurrentUser UserDetails userDetails) {
			return userDetails;
		}

		@QueryMapping
		public String username(@AuthenticationPrincipal(expression = "username") String username) {
			return username;
		}

		@QueryMapping
		public UserDetails beanName(@AuthenticationPrincipal(expression = "@bean.convert(#this)") UserDetails userDetails) {
			return userDetails;
		}

		@QueryMapping
		public String errorOnInvalidType(@AuthenticationPrincipal(errorOnInvalidType = true) String userDetails) {
			return userDetails;
		}

		@QueryMapping
		public String invalidType(@AuthenticationPrincipal String userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Mono<UserDetails> noParameter(Mono<UserDetails> userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Mono<UserDetails> argument(@Argument Mono<UserDetails> userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Mono<UserDetails> userDetails(@AuthenticationPrincipal Mono<UserDetails> userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Mono<UserDetails> currentUser(@CurrentUser Mono<UserDetails> userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Mono<String> username(@AuthenticationPrincipal(expression = "username") Mono<String> username) {
			return username;
		}

		@QueryMapping
		public Mono<UserDetails> beanName(@AuthenticationPrincipal(expression = "@bean.convert(#this)") Mono<UserDetails> userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Mono<String> errorOnInvalidType(@AuthenticationPrincipal(errorOnInvalidType = true) Mono<String> userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Publisher<UserDetails> errorOnInvalidType(@AuthenticationPrincipal(errorOnInvalidType = true) TestPublisher<UserDetails> userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Mono<String> invalidType(@AuthenticationPrincipal Mono<String> userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Publisher<UserDetails> publisher(@AuthenticationPrincipal Publisher<UserDetails> userDetails) {
			return userDetails;
		}

		@QueryMapping
		public Publisher<UserDetails> publisher(@AuthenticationPrincipal TestPublisher<UserDetails> userDetails) {
			return userDetails;
		}
	}

	@AuthenticationPrincipal
	@Retention(RetentionPolicy.RUNTIME)
	public @interface CurrentUser {
	}
}