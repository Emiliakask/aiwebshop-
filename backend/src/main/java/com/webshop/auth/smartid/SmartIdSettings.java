package com.webshop.auth.smartid;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Smart-ID configuration from backend/.env (docs/auth.md). Demo and production never mix: the
 * environment decides the SK URL, the scheme name in the signed data and the trusted CA certificates.
 */
@Component
public class SmartIdSettings {

	public enum Environment {
		DEMO("https://sid.demo.sk.ee/smart-id-rp/v3", "smart-id-demo"),
		PRODUCTION("https://rp-api.smart-id.com/v3", "smart-id");

		final String defaultHostUrl;
		/** First field of the signed ACSP_V2 data. SK's docs only show "smart-id"; demo signs "smart-id-demo". */
		final String schemeName;

		Environment(String defaultHostUrl, String schemeName) {
			this.defaultHostUrl = defaultHostUrl;
			this.schemeName = schemeName;
		}

		public String code() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	static final String DEMO_RP_UUID = "00000000-0000-4000-8000-000000000000";
	static final String DEMO_RP_NAME = "DEMO";

	private final Environment environment;
	private final String hostUrl;
	private final String rpUuid;
	private final String rpName;
	private final boolean testAccountEnabled;
	private final boolean checkRevocation;
	private final String displayText;

	public SmartIdSettings(@Value("${app.smartid.environment}") String environment,
			@Value("${app.smartid.host-url:}") String hostUrl,
			@Value("${app.smartid.rp-uuid:}") String rpUuid,
			@Value("${app.smartid.rp-name:}") String rpName,
			@Value("${app.smartid.test-account-enabled:false}") boolean testAccountEnabled,
			@Value("${app.smartid.check-revocation:true}") boolean checkRevocation,
			@Value("${app.smartid.display-text}") String displayText) {
		this.environment = parse(environment);
		boolean demo = this.environment == Environment.DEMO;
		this.hostUrl = blank(hostUrl) ? this.environment.defaultHostUrl : hostUrl.replaceAll("/+$", "");
		this.rpUuid = blank(rpUuid) && demo ? DEMO_RP_UUID : rpUuid;
		this.rpName = blank(rpName) && demo ? DEMO_RP_NAME : rpName;
		this.testAccountEnabled = testAccountEnabled;
		this.checkRevocation = checkRevocation;
		this.displayText = displayText;

		if (!demo) {
			if (testAccountEnabled) {
				throw new IllegalStateException("SMARTID_TEST_ACCOUNT must be false when SMARTID_ENV=production");
			}
			if (blank(this.rpUuid) || DEMO_RP_UUID.equals(this.rpUuid) || blank(this.rpName)
					|| DEMO_RP_NAME.equalsIgnoreCase(this.rpName)) {
				throw new IllegalStateException("SMARTID_RP_UUID and SMARTID_RP_NAME from the SK contract are required "
						+ "when SMARTID_ENV=production");
			}
			if (this.hostUrl.contains(".demo.")) {
				throw new IllegalStateException("SMARTID_HOST_URL points to the SK demo service but SMARTID_ENV=production");
			}
			if (!checkRevocation) {
				throw new IllegalStateException("Certificate revocation checks cannot be turned off in production");
			}
		}
		if (blank(displayText) || displayText.length() > 200) {
			throw new IllegalStateException("app.smartid.display-text must be 1-200 characters");
		}
	}

	private static Environment parse(String value) {
		try {
			return Environment.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new IllegalStateException("SMARTID_ENV must be demo or production, was: " + value);
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	public Environment environment() {
		return environment;
	}

	public String hostUrl() {
		return hostUrl;
	}

	public String rpUuid() {
		return rpUuid;
	}

	public String rpName() {
		return rpName;
	}

	public String schemeName() {
		return environment.schemeName;
	}

	/** The test account is only ever available in demo. */
	public boolean testAccountEnabled() {
		return environment == Environment.DEMO && testAccountEnabled;
	}

	public boolean checkRevocation() {
		return checkRevocation;
	}

	public String displayText() {
		return displayText;
	}
}
