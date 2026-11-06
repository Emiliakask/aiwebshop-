package com.webshop.payments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The enabled providers, in the order of app.payments.enabled. */
@Component
public class PaymentProviders {

	private final List<PaymentProvider> enabled = new ArrayList<>();

	public PaymentProviders(List<PaymentProvider> all, @Value("${app.payments.enabled}") String enabledCodes) {
		Map<String, PaymentProvider> byCode = all.stream()
				.collect(Collectors.toMap(PaymentProvider::code, Function.identity()));
		for (String code : Arrays.stream(enabledCodes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()) {
			PaymentProvider provider = byCode.get(code);
			if (provider == null) {
				throw new IllegalStateException("app.payments.enabled lists unknown payment provider: " + code);
			}
			enabled.add(provider);
		}
	}

	public Optional<PaymentProvider> find(String code) {
		return enabled.stream().filter(p -> p.code().equals(code)).findFirst();
	}

	/** Used until checkout lets the customer choose between several providers. */
	public Optional<PaymentProvider> defaultProvider() {
		return enabled.stream().findFirst();
	}
}
