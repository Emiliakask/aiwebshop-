package com.webshop.auth.smartid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Date;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AuthenticationResponseValidatorTest {

	private final SmartIdFixture fixture = SmartIdFixture.load();
	private final AuthenticationResponseValidator validator = new AuthenticationResponseValidator(
			SmartIdFixture.demoSettings());

	@Test
	void acceptsTheRealSignedDemoResponse() {
		SmartIdCustomer customer = validator.validate(fixture.status(), fixture.pending());

		assertThat(customer.country()).isEqualTo("EE");
		assertThat(customer.personalCode()).isEqualTo("40504040001");
		assertThat(customer.fullName()).isEqualTo("OK TEST");
		assertThat(customer.maskedPersonalCode()).isEqualTo("4050404****");
		assertThat(customer.toString()).doesNotContain("40504040001");
	}

	@Test
	void rejectsAResponseForADifferentChallenge() {
		byte[] other = new byte[64];
		PendingLogin pending = new PendingLogin("s", Base64.getEncoder().encodeToString(other), fixture.interactions,
				"SHA3-512", "Notification", SmartIdFixture.IDENTITY);

		assertInvalid(fixture.status(), pending);
	}

	@Test
	void rejectsATamperedServerRandom() {
		SessionStatus status = fixture.status();
		SessionStatus.Signature s = status.signature();
		SessionStatus tampered = new SessionStatus(status.state(), status.result(), status.signatureProtocol(),
				new SessionStatus.Signature(s.value(), "AAAA" + s.serverRandom().substring(4), s.userChallenge(),
						s.flowType(), s.signatureAlgorithm(), s.signatureAlgorithmParameters()),
				status.cert(), status.interactionTypeUsed());

		assertInvalid(tampered, fixture.pending());
	}

	@Test
	void rejectsACertificateOfAnotherPerson() {
		PendingLogin someoneElse = new PendingLogin("s", fixture.rpChallenge, fixture.interactions, "SHA3-512",
				"Notification", "PNOEE-30403039917");

		assertInvalid(fixture.status(), someoneElse);
	}

	@Test
	void rejectsAnUnexpectedFlowType() {
		PendingLogin qr = new PendingLogin("s", fixture.rpChallenge, fixture.interactions, "SHA3-512", "QR",
				SmartIdFixture.IDENTITY);

		assertInvalid(fixture.status(), qr);
	}

	@Test
	void rejectsADifferentRelyingPartyName() {
		var otherRp = new AuthenticationResponseValidator(
				new SmartIdSettings("demo", "", "", "Other shop", false, false, "Log in to Webshop"));

		assertThatThrownBy(() -> otherRp.validate(fixture.status(), fixture.pending()))
				.isInstanceOf(SmartIdException.class);
	}

	@Test
	void productionDoesNotTrustSkDemoCertificates() {
		var production = new AuthenticationResponseValidator(new SmartIdSettings("production", "",
				"11111111-2222-4333-8444-555555555555", "Webshop", false, true, "Log in to Webshop"));

		assertThatThrownBy(() -> production.validate(fixture.status(), fixture.pending()))
				.isInstanceOf(SmartIdException.class)
				.hasMessageContaining("certificate chain");
	}

	@Test
	void rejectsAnExpiredCertificate() {
		var later = new AuthenticationResponseValidator(SmartIdFixture.demoSettings(),
				() -> Date.from(Instant.parse("2030-01-01T00:00:00Z")));

		assertThatThrownBy(() -> later.validate(fixture.status(), fixture.pending()))
				.isInstanceOf(SmartIdException.class)
				.hasMessageContaining("certificate chain");
	}

	@Test
	void rejectsAnyResultButOk() {
		SessionStatus status = fixture.status();
		SessionStatus refused = new SessionStatus("COMPLETE", new SessionStatus.Result("USER_REFUSED", null),
				status.signatureProtocol(), status.signature(), status.cert(), status.interactionTypeUsed());

		assertInvalid(refused, fixture.pending());
	}

	@Test
	void derOidMatchesTheStandardEncoding() {
		// 1.3.6.1.5.5.7.3.2 (clientAuth) is 06 08 2B 06 01 05 05 07 03 02
		assertThat(AuthenticationResponseValidator.derOid("1.3.6.1.5.5.7.3.2"))
				.containsExactly(0x06, 0x08, 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x03, 0x02);
	}

	private void assertInvalid(SessionStatus status, PendingLogin pending) {
		assertThatThrownBy(() -> validator.validate(status, pending))
				.isInstanceOfSatisfying(SmartIdException.class,
						e -> assertThat(e.reason()).isEqualTo(SmartIdException.Reason.INVALID_RESPONSE));
	}
}
