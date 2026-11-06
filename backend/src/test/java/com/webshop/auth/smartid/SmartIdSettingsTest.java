package com.webshop.auth.smartid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SmartIdSettingsTest {

	private static final String RP_UUID = "11111111-2222-4333-8444-555555555555";

	@Test
	void demoUsesSkDemoServiceAndDemoRelyingParty() {
		SmartIdSettings demo = new SmartIdSettings("demo", "", "", "", true, true, "Log in");

		assertThat(demo.hostUrl()).isEqualTo("https://sid.demo.sk.ee/smart-id-rp/v3");
		assertThat(demo.rpUuid()).isEqualTo("00000000-0000-4000-8000-000000000000");
		assertThat(demo.rpName()).isEqualTo("DEMO");
		assertThat(demo.schemeName()).isEqualTo("smart-id-demo");
		assertThat(demo.testAccountEnabled()).isTrue();
	}

	@Test
	void productionUsesTheRealService() {
		SmartIdSettings production = new SmartIdSettings("production", "", RP_UUID, "Webshop", false, true, "Log in");

		assertThat(production.hostUrl()).isEqualTo("https://rp-api.smart-id.com/v3");
		assertThat(production.schemeName()).isEqualTo("smart-id");
		assertThat(production.testAccountEnabled()).isFalse();
	}

	@Test
	void productionRefusesToStartWithTheTestAccount() {
		assertThatThrownBy(() -> new SmartIdSettings("production", "", RP_UUID, "Webshop", true, true, "Log in"))
				.hasMessageContaining("SMARTID_TEST_ACCOUNT");
	}

	@Test
	void productionRefusesToStartWithDemoValues() {
		assertThatThrownBy(() -> new SmartIdSettings("production", "", "", "", false, true, "Log in"))
				.hasMessageContaining("SMARTID_RP_UUID");
		assertThatThrownBy(() -> new SmartIdSettings("production", "", SmartIdSettings.DEMO_RP_UUID, "DEMO", false,
				true, "Log in")).hasMessageContaining("SMARTID_RP_UUID");
		assertThatThrownBy(() -> new SmartIdSettings("production", "https://sid.demo.sk.ee/smart-id-rp/v3", RP_UUID,
				"Webshop", false, true, "Log in")).hasMessageContaining("demo");
		assertThatThrownBy(() -> new SmartIdSettings("production", "", RP_UUID, "Webshop", false, false, "Log in"))
				.hasMessageContaining("revocation");
	}

	@Test
	void unknownEnvironmentIsRefused() {
		assertThatThrownBy(() -> new SmartIdSettings("staging", "", "", "", false, true, "Log in"))
				.hasMessageContaining("SMARTID_ENV");
	}
}
