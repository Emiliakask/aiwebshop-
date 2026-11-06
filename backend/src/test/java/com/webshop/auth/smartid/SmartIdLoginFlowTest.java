package com.webshop.auth.smartid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmartIdLoginFlowTest {

	static final String START = """
			{"country":"EE","personalCode":"40504040001"}
			""";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	SmartIdClient sk;

	@MockitoBean
	SmartIdChallenges challenges;

	final SmartIdFixture fixture = SmartIdFixture.load();
	final MockHttpSession session = new MockHttpSession();

	@BeforeEach
	void replayRecordedDemoLogin() {
		when(challenges.next()).thenReturn(fixture.challengeBytes());
		when(sk.startNotificationAuthentication(eq(SmartIdFixture.IDENTITY), eq(fixture.rpChallenge), eq("SHA3-512"),
				eq(fixture.interactions))).thenReturn("sk-session-1");
	}

	@Test
	void chooseNumberLoginShowsCodeThenLogsTheCustomerIn() throws Exception {
		start().andExpect(status().isOk()).andExpect(jsonPath("$.verificationCode").value("2086"));

		when(sk.sessionStatus("sk-session-1")).thenReturn(running());
		poll().andExpect(jsonPath("$.state").value("running"));
		mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());

		String sessionIdBefore = session.getId();
		when(sk.sessionStatus("sk-session-1")).thenReturn(fixture.status());
		poll().andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("complete"))
				.andExpect(jsonPath("$.me.type").value("customer"))
				.andExpect(jsonPath("$.me.name").value("OK TEST"))
				.andExpect(jsonPath("$.me.personalCode").value("4050404****"));
		assertThat(session.getId()).as("new session id after login").isNotEqualTo(sessionIdBefore);

		mvc.perform(get("/api/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("customer"))
				.andExpect(jsonPath("$.country").value("EE"))
				.andExpect(jsonPath("$.email").doesNotExist());

		// The pending login is used up.
		poll().andExpect(status().isNotFound());
	}

	@Test
	void customerIsNotAnAdmin() throws Exception {
		start();
		when(sk.sessionStatus("sk-session-1")).thenReturn(fixture.status());
		poll().andExpect(jsonPath("$.state").value("complete"));

		mvc.perform(post("/api/products").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void forgedResponseDoesNotLogIn() throws Exception {
		start();
		SessionStatus real = fixture.status();
		SessionStatus.Signature s = real.signature();
		SessionStatus forged = new SessionStatus(real.state(), real.result(), real.signatureProtocol(),
				new SessionStatus.Signature(s.value(), s.serverRandom(), "x" + s.userChallenge().substring(1),
						s.flowType(), s.signatureAlgorithm(), s.signatureAlgorithmParameters()),
				real.cert(), real.interactionTypeUsed());
		when(sk.sessionStatus("sk-session-1")).thenReturn(forged);

		poll().andExpect(jsonPath("$.state").value("failed")).andExpect(jsonPath("$.reason").value("error"));
		mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void userRefusingIsReported() throws Exception {
		start();
		when(sk.sessionStatus("sk-session-1")).thenReturn(new SessionStatus("COMPLETE",
				new SessionStatus.Result("WRONG_VC", null), null, null, null, null));

		poll().andExpect(jsonPath("$.state").value("failed")).andExpect(jsonPath("$.reason").value("wrong_vc"));
	}

	@Test
	void personNotInSmartId() throws Exception {
		when(sk.startNotificationAuthentication(any(), any(), any(), any()))
				.thenThrow(new SmartIdException(SmartIdException.Reason.NOT_FOUND, "No Smart-ID account"));

		start().andExpect(status().isNotFound());
	}

	@Test
	void invalidPersonalCodeNeverReachesSk() throws Exception {
		mvc.perform(post("/api/auth/smart-id/notification").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"country\":\"EE\",\"personalCode\":\"123\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/auth/smart-id/notification").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"country\":\"FI\",\"personalCode\":\"40504040001\"}"))
				.andExpect(status().isBadRequest());
		verify(sk, never()).startNotificationAuthentication(any(), any(), any(), any());
	}

	@Test
	void startNeedsCsrfToken() throws Exception {
		mvc.perform(post("/api/auth/smart-id/notification").session(session)
				.contentType(MediaType.APPLICATION_JSON).content(START))
				.andExpect(status().isForbidden());
	}

	@Test
	void testAccountIsOffUnlessEnabled() throws Exception {
		mvc.perform(get("/api/auth/smart-id/config"))
				.andExpect(jsonPath("$.environment").value("demo"))
				.andExpect(jsonPath("$.testAccount").value(false));
		mvc.perform(post("/api/auth/test-account").session(session).with(csrf())).andExpect(status().isNotFound());
	}

	private ResultActions start() throws Exception {
		return mvc.perform(post("/api/auth/smart-id/notification").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(START));
	}

	private ResultActions poll() throws Exception {
		return mvc.perform(post("/api/auth/smart-id/status").session(session).with(csrf()));
	}

	private static SessionStatus running() {
		return new SessionStatus("RUNNING", null, null, null, null, null);
	}
}
