package com.akrcode.akr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication
public class AkrApplication {

	public static void main(String[] args) {
		SpringApplication.run(AkrApplication.class, args);
	}

	@Configuration
	static class SecurityConfig {
//
//		@Bean
//		public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//			http.csrf().disable()
//					.addFilterAfter(new JWTAuthorizationFilter(), UsernamePasswordAuthenticationFilter.class)
//					.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
//
//			return http.build();
//		}
		
		
		@Bean
		public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		    http.csrf().disable()
		        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		    return http.build();
		}

	}
}
