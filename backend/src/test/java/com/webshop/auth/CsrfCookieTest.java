package com.webshop.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Own Spring context on purpose: spring-security-test's csrf() helper, used by other tests, swaps the
 * CSRF token repository in the shared filter, which would hide the real cookie behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieTest {

	@Autowired
	MockMvc mvc;

	@Test
	void csrfCookieIsReadableByTheFrontend() throws Exception {
		mvc.perform(get("/api/products"))
				.andExpect(cookie().exists("XSRF-TOKEN"))
				.andExpect(cookie().httpOnly("XSRF-TOKEN", false));
	}
}
