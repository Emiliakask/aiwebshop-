package com.webshop.auth.smartid;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.smartid.test-account-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmartIdTestAccountTest {

	@Autowired
	MockMvc mvc;

	@Test
	void demoTestAccountLogsIn() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(get("/api/auth/smart-id/config")).andExpect(jsonPath("$.testAccount").value(true));

		mvc.perform(post("/api/auth/test-account").session(session).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("customer"))
				.andExpect(jsonPath("$.name").value("Test User"));
		mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.type").value("customer"));
	}
}
