package com.webshop.auth.smartid;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Calls SK's Smart-ID RP API v3 directly. Kept small and separate so tests can replace it. */
@Component
public class SmartIdClient {

	/** SK's long-poll wait per status call. Short, because our frontend polls us every ~2 s. */
	static final int STATUS_TIMEOUT_MS = 1000;

	private final RestClient http;
	private final SmartIdSettings settings;

	public SmartIdClient(SmartIdSettings settings) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(15));
		this.http = RestClient.builder().baseUrl(settings.hostUrl()).requestFactory(requestFactory).build();
		this.settings = settings;
	}

	private record StartResponse(String sessionID) {
	}

	/**
	 * Starts a notification-based authentication for "PNOEE-40504040001" and returns SK's session id.
	 *
	 * @throws SmartIdException NOT_FOUND when the person has no usable Smart-ID account
	 */
	public String startNotificationAuthentication(String semanticsIdentifier, String rpChallenge, String hashAlgorithm,
			String interactions) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("relyingPartyUUID", settings.rpUuid());
		body.put("relyingPartyName", settings.rpName());
		body.put("certificateLevel", "QUALIFIED");
		body.put("signatureProtocol", "ACSP_V2");
		body.put("signatureProtocolParameters", Map.of(
				"rpChallenge", rpChallenge,
				"signatureAlgorithm", "rsassa-pss",
				"signatureAlgorithmParameters", Map.of("hashAlgorithm", hashAlgorithm)));
		body.put("interactions", interactions);
		body.put("vcType", "numeric4");
		try {
			StartResponse response = http.post()
					.uri("/authentication/notification/etsi/{id}", semanticsIdentifier)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(StartResponse.class);
			if (response == null || response.sessionID() == null) {
				throw new SmartIdException(SmartIdException.Reason.UNAVAILABLE, "Smart-ID returned no session");
			}
			return response.sessionID();
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
				throw new SmartIdException(SmartIdException.Reason.NOT_FOUND, "No Smart-ID account");
			}
			throw new SmartIdException(SmartIdException.Reason.UNAVAILABLE,
					"Smart-ID start failed with HTTP " + e.getStatusCode().value(), e);
		} catch (RestClientException e) {
			throw new SmartIdException(SmartIdException.Reason.UNAVAILABLE, "Smart-ID is not reachable", e);
		}
	}

	/** One long-poll of the session status. */
	public SessionStatus sessionStatus(String sessionId) {
		try {
			SessionStatus status = http.get()
					.uri("/session/{id}?timeoutMs={timeout}", sessionId, STATUS_TIMEOUT_MS)
					.retrieve()
					.body(SessionStatus.class);
			if (status == null) {
				throw new SmartIdException(SmartIdException.Reason.UNAVAILABLE, "Empty Smart-ID session status");
			}
			return status;
		} catch (RestClientResponseException e) {
			throw new SmartIdException(SmartIdException.Reason.UNAVAILABLE,
					"Smart-ID status failed with HTTP " + e.getStatusCode().value(), e);
		} catch (RestClientException e) {
			throw new SmartIdException(SmartIdException.Reason.UNAVAILABLE, "Smart-ID is not reachable", e);
		}
	}
}
