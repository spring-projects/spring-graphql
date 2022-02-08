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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.test.publisher.TestPublisher;
import reactor.util.context.Context;

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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Tests for {@link AuthenticationPrincipalArgumentResolver}.
 *
 * @author Rob Winch
 */
class AuthenticationPrincipalArgumentResolverTests {

	private final static Class<String> STRING_CLASS = String.class;

	private final static Class<UserDetails> USER_DETAILS_CLASS = UserDetails.class;

	private final static Class<?> MONO_USER_DETAILS_CLASS =
			ResolvableType.forClassWithGenerics(Mono.class, UserDetails.class).getRawClass();

	private final static Class<?> MONO_STRING_CLASS =
			ResolvableType.forClassWithGenerics(Mono.class, String.class).getRawClass();

	private final static Class<?> PUBLISHER_USER_DETAILS_CLASS =
			ResolvableType.forClassWithGenerics(Publisher.class, UserDetails.class).getRawClass();

	private final static Class<?> TESTPUBLISHER_USER_DETAILS_CLASS =
			ResolvableType.forClassWithGenerics(TestPublisher.class, UserDetails.class).getRawClass();


	private final AuthenticationPrincipalArgumentResolver resolver =
			new AuthenticationPrincipalArgumentResolver((beanName, context) -> new PrincipalConverter());


	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
	}

	
	@Test
	void supportsParameterWhenNoAnnotation() {
		MethodParameter parameter = firstParameter(UserController.class, "noParameter", USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(parameter)).isFalse();
	}

	@Test
	void supportsParameterWhenWrongAnnotation() {
		MethodParameter parameter = firstParameter(UserController.class, "argument", USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(parameter)).isFalse();
	}

	@Test
	void supportsParameterWhenCurrentUser() {
		MethodParameter parameter = firstParameter(UserController.class, "currentUser", USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(parameter)).isTrue();
	}

	@Test
	void supportsParameterWhenAuthenticationPrincipal() {
		MethodParameter parameter = firstParameter(UserController.class, "userDetails", USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(parameter)).isTrue();
	}

	@Test
	void resolveArgumentWhenAuthenticationPrincipal() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "userDetails", USER_DETAILS_CLASS);
		Mono<UserDetails> details = (Mono<UserDetails>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenCurrentUser() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "currentUser", USER_DETAILS_CLASS);
		Mono<UserDetails> details = (Mono<UserDetails>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenNoSecurityContext() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "currentUser", USER_DETAILS_CLASS);
		Mono<UserDetails> details = (Mono<UserDetails>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.block()).isNull();
	}

	@Test
	void resolveArgumentWhenSecurityContextHolder() throws Exception {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(usernamePasswordAuthentication());
		SecurityContextHolder.setContext(context);
		MethodParameter parameter = firstParameter(UserController.class, "currentUser", USER_DETAILS_CLASS);
		Mono<UserDetails> details = (Mono<UserDetails>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenAuthenticationPrincipalUsername() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "username", STRING_CLASS);
		Mono<String> details = (Mono<String>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.contextWrite(authenticationContext()).block()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenBeanName() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "beanName", USER_DETAILS_CLASS);
		Mono<UserDetails> details = (Mono<UserDetails>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenErrorOnInvalidType() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "errorOnInvalidType", String.class);
		Mono<Object> details = (Mono<Object>) this.resolver.resolveArgument(parameter, null);
		assertThatExceptionOfType(ClassCastException.class)
				.isThrownBy(() -> details.contextWrite(authenticationContext()).block());
	}

	@Test
	void resolveArgumentWhenInvalidType() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "invalidType", STRING_CLASS);
		Mono<Mono<Object>> details = (Mono<Mono<Object>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.flatMap(u -> u).contextWrite(authenticationContext()).block()).isNull();
	}

	@Test
	void supportsParameterWhenMonoNoAnnotation() {
		MethodParameter parameter = firstParameter(UserController.class, "noParameter", MONO_USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(parameter)).isFalse();
	}

	@Test
	void supportsParameterWhenMonoWrongAnnotation() {
		MethodParameter parameter = firstParameter(UserController.class, "argument", MONO_USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(parameter)).isFalse();
	}

	@Test
	void supportsParameterWhenMonoCurrentUser() {
		MethodParameter parameter = firstParameter(UserController.class, "currentUser", MONO_USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(parameter)).isTrue();
	}

	@Test
	void supportsParameterWhenMonoAuthenticationPrincipal() {
		MethodParameter parameter = firstParameter(UserController.class, "userDetails", MONO_USER_DETAILS_CLASS);
		assertThat(this.resolver.supportsParameter(parameter)).isTrue();
	}

	@Test
	void resolveArgumentWhenMonoAuthenticationPrincipal() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "userDetails", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> details = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.block().contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoCurrentUser() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "currentUser", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> details = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.block().contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoNoSecurityContext() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "currentUser", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> details = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.block().block()).isNull();
	}

	@Test
	void resolveArgumentWhenMonoSecurityContextHolder() throws Exception {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(usernamePasswordAuthentication());
		SecurityContextHolder.setContext(context);
		MethodParameter parameter = firstParameter(UserController.class, "currentUser", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> details = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.block().contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoAuthenticationPrincipalUsername() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "username", MONO_STRING_CLASS);
		Mono<Mono<String>> details = (Mono<Mono<String>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.block().contextWrite(authenticationContext()).block()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoBeanName() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "beanName", MONO_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> details = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.flatMap(u -> u).contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenMonoErrorOnInvalidType() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "errorOnInvalidType", MONO_STRING_CLASS);
		Mono<Mono<Object>> details = (Mono<Mono<Object>>) this.resolver.resolveArgument(parameter, null);
		assertThatExceptionOfType(ClassCastException.class)
				.isThrownBy(() -> details.flatMap(u -> u).contextWrite(authenticationContext()).block());
	}

	@Test
	void resolveArgumentWhenMonoInvalidType() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "invalidType", MONO_STRING_CLASS);
		Mono<Mono<Object>> details = (Mono<Mono<Object>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.flatMap(u -> u).contextWrite(authenticationContext()).block()).isNull();
	}

	@Test
	void resolveArgumentWhenPublisher() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "publisher", PUBLISHER_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> details = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.block().contextWrite(authenticationContext()).block().getUsername()).isEqualTo("user");
	}

	@Test
	void resolveArgumentWhenTestPublisherThenEmptyMono() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "publisher", TESTPUBLISHER_USER_DETAILS_CLASS);
		Mono<TestPublisher<UserDetails>> details = (Mono<TestPublisher<UserDetails>>) this.resolver.resolveArgument(parameter, null);
		assertThat(details.contextWrite(authenticationContext()).block()).isNull();
	}

	@Test
	void resolveArgumentWhenTestPublisherAndErrorOnInvalidType() throws Exception {
		MethodParameter parameter = firstParameter(UserController.class, "errorOnInvalidType", TESTPUBLISHER_USER_DETAILS_CLASS);
		Mono<Mono<UserDetails>> details = (Mono<Mono<UserDetails>>) this.resolver.resolveArgument(parameter, null);
		assertThatExceptionOfType(ClassCastException.class)
				.isThrownBy(() -> details.flatMap(u -> u).contextWrite(authenticationContext()).block());
	}

	private MethodParameter firstParameter(Class<?> clazz, String methodName, Class<?>... paramTypes) {
		Method method = ClassUtils.getMethod(clazz, methodName, paramTypes);
		return methodParam(method, 0);
	}

	private MethodParameter methodParam(Method method, int index) {
		MethodParameter parameter = new SynthesizingMethodParameter(method, index);
		parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
		return parameter;
	}

	private static Function<Context, Context> authenticationContext() {
		return (context) -> ReactiveSecurityContextHolder.withAuthentication(usernamePasswordAuthentication());
	}

	private static Authentication usernamePasswordAuthentication() {
		UserDetails details = userDetails();
		return new UsernamePasswordAuthenticationToken(details, details.getPassword(), details.getAuthorities());
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
		public UserDetails beanName(
				@AuthenticationPrincipal(expression = "@bean.convert(#this)") UserDetails userDetails) {

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
		public Mono<UserDetails> beanName(
				@AuthenticationPrincipal(expression = "@bean.convert(#this)") Mono<UserDetails> userDetails) {

			return userDetails;
		}

		@QueryMapping
		public Mono<String> errorOnInvalidType(
				@AuthenticationPrincipal(errorOnInvalidType = true) Mono<String> userDetails) {

			return userDetails;
		}

		@QueryMapping
		public Publisher<UserDetails> errorOnInvalidType(
				@AuthenticationPrincipal(errorOnInvalidType = true) TestPublisher<UserDetails> userDetails) {

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