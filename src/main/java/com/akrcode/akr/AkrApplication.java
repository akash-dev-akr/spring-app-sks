package com.akrcode.akr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Main application class for AKR system.
 */
@SpringBootApplication
public class AkrApplication {

	public static void main(String[] args) {
		SpringApplication.run(AkrApplication.class, args);
	}

	/**
	 * Spring Security configuration.
	 */
	@Configuration
	static class SecurityConfig {

		@Bean
		public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
			http.csrf().disable().headers(headers -> headers.frameOptions().sameOrigin() // ✅ Allow same-origin iframe
			).authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

			return http.build();
		}
	}
}
