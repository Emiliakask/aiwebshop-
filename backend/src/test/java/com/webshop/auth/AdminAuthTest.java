package com.webshop.auth;

import static com.webshop.TestSupport.admin;
import static com.webshop.TestSupport.someGoogleUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthTest {

	@Autowired
	MockMvc mvc;

	private final AdminOidcUserService service = new AdminOidcUserService(List.of(" Admin@Example.com ", ""));

	@Test
	void allowListedVerifiedEmailBecomesAdmin() {
		OidcUser admin = service.toAdmin(googleUser("admin@example.com", true));
		assertThat(admin.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
	}

	@Test
	void emailNotOnAllowListIsRejected() {
		assertThatThrownBy(() -> service.toAdmin(googleUser("someone@gmail.com", true)))
				.isInstanceOf(OAuth2AuthenticationException.class);
	}

	@Test
	void unverifiedEmailIsRejectedEvenIfOnAllowList() {
		assertThatThrownBy(() -> service.toAdmin(googleUser("admin@example.com", false)))
				.isInstanceOf(OAuth2AuthenticationException.class);
	}

	@Test
	void meIs401WhenLoggedOut() throws Exception {
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void meIs401ForNonAdminGoogleUser() throws Exception {
		mvc.perform(get("/api/me").with(someGoogleUser())).andExpect(status().isUnauthorized());
	}

	@Test
	void meDescribesTheAdmin() throws Exception {
		mvc.perform(get("/api/me").with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("admin"))
				.andExpect(jsonPath("$.email").value("admin@example.com"));
	}

	@Test
	void logoutReturnsNoContent() throws Exception {
		mvc.perform(post("/api/logout").with(admin()).with(csrf())).andExpect(status().isNoContent());
	}

	@Test
	void adminApiNeedsAdmin() throws Exception {
		mvc.perform(get("/api/admin/anything")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/admin/anything").with(someGoogleUser())).andExpect(status().isForbidden());
	}

	private static OidcUser googleUser(String email, boolean verified) {
		OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
				Map.of("sub", "123", "email", email, "email_verified", verified));
		return new DefaultOidcUser(List.of(), token);
	}
}
