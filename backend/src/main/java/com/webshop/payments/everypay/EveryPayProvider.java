package com.webshop.payments.everypay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.webshop.common.Money;
import com.webshop.payments.PaymentException;
import com.webshop.payments.PaymentProvider;
import com.webshop.payments.PaymentStatus;

/**
 * EveryPay (LHV) API v4, hosted payment page (docs/payments.md).
 * Config: EVERYPAY_API_URL, EVERYPAY_API_USERNAME, EVERYPAY_API_SECRET, EVERYPAY_ACCOUNT_NAME in backend/.env.
 */
@Component
public class EveryPayProvider implements PaymentProvider {

	static final Set<String> PAID_STATES = Set.of("settled", "authorised");
	static final Set<String> FAILED_STATES = Set.of("failed", "abandoned", "voided");

	private final RestClient http;
	private final String apiUsername;
	private final String accountName;
	private final String locale;
	private final boolean configured;

	public EveryPayProvider(@Value("${app.payments.everypay.api-url}") String apiUrl,
			@Value("${app.payments.everypay.api-username:}") String apiUsername,
			@Value("${app.payments.everypay.api-secret:}") String apiSecret,
			@Value("${app.payments.everypay.account-name}") String accountName,
			@Value("${app.payments.everypay.locale:en}") String locale) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(20));
		String basic = Base64.getEncoder().encodeToString((apiUsername + ":" + apiSecret).getBytes());
		this.http = RestClient.builder()
				.baseUrl(apiUrl.replaceAll("/+$", ""))
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
				.build();
		this.apiUsername = apiUsername;
		this.accountName = accountName;
		this.locale = locale;
		this.configured = !apiUsername.isBlank() && !apiSecret.isBlank();
	}

	@Override
	public String code() {
		return "everypay";
	}

	@Override
	public String displayName() {
		return "EveryPay";
	}

	record OneoffResponse(@JsonProperty("payment_reference") String paymentReference,
			@JsonProperty("payment_link") String paymentLink) {
	}

	record StatusResponse(@JsonProperty("payment_reference") String paymentReference,
			@JsonProperty("payment_state") String paymentState,
			@JsonProperty("initial_amount") BigDecimal initialAmount,
			@JsonProperty("account_name") String accountName,
			@JsonProperty("currency") String currency) {
	}

	@Override
	public Started start(Request request) {
		requireConfigured();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("api_username", apiUsername);
		body.put("account_name", accountName);
		body.put("amount", toEuros(request.amount()));
		body.put("order_reference", request.orderId());
		body.put("nonce", UUID.randomUUID().toString());
		body.put("timestamp", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
		body.put("customer_url", request.returnUrl());
		body.put("email", request.email());
		body.put("locale", locale);
		try {
			OneoffResponse response = http.post().uri("/payments/oneoff")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(OneoffResponse.class);
			if (response == null || response.paymentReference() == null || response.paymentLink() == null) {
				throw new PaymentException("EveryPay returned no payment link");
			}
			return new Started(response.paymentReference(), response.paymentLink());
		} catch (RestClientResponseException e) {
			throw new PaymentException("EveryPay refused the payment: HTTP " + e.getStatusCode().value() + " "
					+ e.getResponseBodyAsString(), e);
		} catch (RestClientException e) {
			throw new PaymentException("EveryPay is not reachable", e);
		}
	}

	@Override
	public Result fetchStatus(String providerReference) {
		requireConfigured();
		try {
			StatusResponse response = http.get()
					.uri("/payments/{ref}?api_username={user}", providerReference, apiUsername)
					.retrieve()
					.body(StatusResponse.class);
			if (response == null || response.paymentState() == null) {
				throw new PaymentException("EveryPay returned no payment state");
			}
			return new Result(providerReference, map(response.paymentState()), amount(response), response.paymentState());
		} catch (RestClientResponseException e) {
			throw new PaymentException("EveryPay status failed: HTTP " + e.getStatusCode().value(), e);
		} catch (RestClientException e) {
			throw new PaymentException("EveryPay is not reachable", e);
		}
	}

	/**
	 * What was charged. The status response has no currency field: the currency is the processing account's,
	 * so we only accept it for our own (EUR) account. Null means "unknown", which never counts as paid.
	 */
	Money amount(StatusResponse response) {
		if (response.initialAmount() == null || !accountName.equals(response.accountName())) {
			return null;
		}
		String currency = response.currency() != null ? response.currency().toUpperCase(java.util.Locale.ROOT) : "EUR";
		return new Money(toCents(response.initialAmount()), currency);
	}

	static PaymentStatus map(String state) {
		if (PAID_STATES.contains(state)) {
			return PaymentStatus.PAID;
		}
		if (FAILED_STATES.contains(state)) {
			return PaymentStatus.FAILED;
		}
		// initial, waiting_for_3ds_response, waiting_for_sca, sent_for_processing, ...
		return PaymentStatus.PENDING;
	}

	/** 1234 cents -> 12.34 exactly (EveryPay wants a decimal amount with 2 digits). */
	static BigDecimal toEuros(Money money) {
		if (!"EUR".equals(money.currency())) {
			throw new PaymentException("EveryPay account is EUR only");
		}
		return BigDecimal.valueOf(money.amount(), 2);
	}

	/** 12.34 -> 1234. Anything with more than 2 decimals is refused rather than rounded. */
	static long toCents(BigDecimal euros) {
		try {
			return euros.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
		} catch (ArithmeticException e) {
			throw new PaymentException("EveryPay reported an amount that is not whole cents: " + euros);
		}
	}

	private void requireConfigured() {
		if (!configured) {
			throw new PaymentException("EveryPay is not configured: set EVERYPAY_API_USERNAME and EVERYPAY_API_SECRET");
		}
	}
}
