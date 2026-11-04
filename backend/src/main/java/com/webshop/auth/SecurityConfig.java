package com.webshop.auth;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AdminOidcUserService adminUsers,
			@Value("${app.frontend-url}") String frontendUrl) {
		http
				.cors(Customizer.withDefaults()) // uses the CORS rules in WebConfig
				// CSRF token in the XSRF-TOKEN cookie; the frontend sends it back as X-XSRF-TOKEN.
				.csrf(csrf -> csrf.spa())
				.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
						.requestMatchers("/api/products", "/api/products/**", "/api/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/**", "/actuator/health", "/error").permitAll()
						.anyRequest().authenticated())
				// API callers get a plain 401 instead of a redirect to Google.
				.exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
						new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), pathPattern("/api/**")))
				.oauth2Login(login -> login
						.userInfoEndpoint(userInfo -> userInfo.oidcUserService(adminUsers))
						.defaultSuccessUrl(frontendUrl + "/admin", true)
						.failureUrl(frontendUrl + "/admin?loginError"))
				.logout(logout -> logout
						.logoutRequestMatcher(pathPattern(HttpMethod.POST, "/api/logout"))
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
		return http.build();
	}

	/** Makes sure the XSRF-TOKEN cookie is sent on every response, so the frontend always has it. */
	static final class CsrfCookieFilter extends OncePerRequestFilter {
		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			if (token != null) {
				token.getToken();
			}
			chain.doFilter(request, response);
		}
	}
}
