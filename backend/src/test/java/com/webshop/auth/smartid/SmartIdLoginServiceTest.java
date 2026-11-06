package com.webshop.auth.smartid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SmartIdLoginServiceTest {

	@Test
	void verificationCodeFollowsSkFormula() {
		// Expected values computed independently (Python hashlib) from SK's documented formula.
		assertThat(SmartIdLoginService.verificationCode(SmartIdFixture.load().challengeBytes())).isEqualTo("2086");
		assertThat(SmartIdLoginService.verificationCode(Base64.getDecoder().decode(
				"GYS+yoah6emAcVDNIajwSs6UB/M95XrDxMzXBUkwQJ9YFDipXXzGpPc7raWcuc2+TEoRc7WvIZ/7dU/iRXenYg==")))
				.isEqualTo("7180");
	}

	@Test
	void interactionsAreTheExactBytesThatWereSigned() {
		assertThat(SmartIdLoginService.interactions("Log in to Webshop")).isEqualTo(SmartIdFixture.load().interactions);
	}

	@Test
	void personalCodesAreCheckedPerCountry() {
		assertThat(SmartIdLoginService.normalize("EE", " 40504040001 ")).isEqualTo("40504040001");
		assertThat(SmartIdLoginService.normalize("LT", "40504040001")).isEqualTo("40504040001");
		assertThat(SmartIdLoginService.normalize("LV", "05040510009")).isEqualTo("050405-10009");
		assertThat(SmartIdLoginService.normalize("LV", "050405-10009")).isEqualTo("050405-10009");
		for (String bad : new String[] { "", "123", "4050404000", "405040400011", "90504040001", "4050404000a" }) {
			assertThatThrownBy(() -> SmartIdLoginService.normalize("EE", bad))
					.isInstanceOf(ResponseStatusException.class);
		}
		assertThatThrownBy(() -> SmartIdLoginService.normalize("LV", "0504-0510009"))
				.isInstanceOf(ResponseStatusException.class);
	}
}
