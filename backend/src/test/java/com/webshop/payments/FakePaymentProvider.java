package com.webshop.payments;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.webshop.common.Money;

/** Test stand-in for a real provider (enabled in application-test.properties). */
@Component
public class FakePaymentProvider implements PaymentProvider {

	private final Map<String, Result> results = new ConcurrentHashMap<>();
	public volatile boolean refuseNextStart;

	@Override
	public String code() {
		return "fake";
	}

	@Override
	public String displayName() {
		return "Fake";
	}

	@Override
	public Started start(Request request) {
		if (refuseNextStart) {
			refuseNextStart = false;
			throw new PaymentException("refused");
		}
		String reference = "ref-" + request.orderId();
		results.put(reference, new Result(reference, PaymentStatus.PENDING, request.amount(), "initial"));
		return new Started(reference, "https://pay.example.com/" + request.orderId());
	}

	@Override
	public Result fetchStatus(String providerReference) {
		Result result = results.get(providerReference);
		if (result == null) {
			throw new PaymentException("unknown payment");
		}
		return result;
	}

	public void answer(String orderId, PaymentStatus status, Money amount) {
		results.put("ref-" + orderId, new Result("ref-" + orderId, status, amount, status.name().toLowerCase()));
	}
}
