package io.spring.sample.graphql;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.DefaultSecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	@Bean
	DefaultSecurityFilterChain springWebFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(c -> c.disable())
				// Demonstrate that method security works
				// Best practice to use both for defense in depth
				.authorizeRequests(requests -> requests
					.anyRequest().permitAll()
				)
				.httpBasic(withDefaults())
				.build();
	}

	@Bean
	public static InMemoryUserDetailsManager userDetailsService() {
		User.UserBuilder userBuilder = User.withDefaultPasswordEncoder();
		UserDetails rob = userBuilder.username("rob").password("rob").roles("USER").build();
		UserDetails admin = userBuilder.username("admin").password("admin").roles("USER", "ADMIN").build();
		return new InMemoryUserDetailsManager(rob, admin);
	}

}
