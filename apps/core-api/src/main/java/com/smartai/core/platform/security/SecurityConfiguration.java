package com.smartai.core.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.smartai.core.platform.api.ApiProblemWriter;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	@Bean
	@Profile("local")
	SecurityFilterChain localSecurity(HttpSecurity http, ApiProblemWriter problemWriter) throws Exception {
		configureStatelessApi(http, problemWriter);
		http.authorizeHttpRequests(requests -> requests
			.requestMatchers(
				"/actuator/health",
				"/actuator/health/liveness",
				"/actuator/health/readiness",
				"/api/core/v1/requirement-drafts",
				"/api/core/v1/requirement-drafts/**",
				"/api/core/v1/recruitment-tasks/**",
				"/api/core/v1/candidate-inputs/**",
				"/api/core/v1/match-runs/**",
				"/api/core/v1/match-results/**",
				"/api/core/v1/position-plan-versions/**",
				"/api/core/v1/knowledge-documents/**",
				"/api/core/v1/knowledge-versions/**",
				"/api/core/v1/knowledge-upload-sessions/**",
				"/api/core/v1/knowledge-evidence/**",
				"/api/core/v1/human-checkpoints/**",
				"/api/core/v1/agent-runs/**").permitAll()
			.anyRequest().denyAll());
		return http.build();
	}

	@Bean
	@Profile("production")
	SecurityFilterChain productionSecurity(HttpSecurity http, ApiProblemWriter problemWriter) throws Exception {
		configureStatelessApi(http, problemWriter);
		http.authorizeHttpRequests(requests -> requests
			.requestMatchers(
				"/actuator/health",
				"/actuator/health/liveness",
				"/actuator/health/readiness").permitAll()
			.anyRequest().denyAll());
		return http.build();
	}

	@Bean
	@Profile("!local & !production")
	SecurityFilterChain defaultDenySecurity(HttpSecurity http, ApiProblemWriter problemWriter) throws Exception {
		configureStatelessApi(http, problemWriter);
		http.authorizeHttpRequests(requests -> requests.anyRequest().denyAll());
		return http.build();
	}

	private static void configureStatelessApi(HttpSecurity http, ApiProblemWriter problemWriter) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(cache -> cache.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) -> problemWriter.write(
					request, response, 401, "AUTH_FAILED", "Authentication is required"))
				.accessDeniedHandler((request, response, exception) -> problemWriter.write(
					request, response, 403, "PERMISSION_DENIED", "Access is denied")))
			.anonymous(Customizer.withDefaults());
	}
}
