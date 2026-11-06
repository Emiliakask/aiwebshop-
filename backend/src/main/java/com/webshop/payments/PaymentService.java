package com.webshop.payments;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.webshop.orders.CustomerOrder;
import com.webshop.orders.OrderService;
import com.webshop.orders.OrderStatus;

/**
 * Connects orders to payment providers (docs/payments.md). An order becomes paid only after the provider
 * confirms it server-to-server, with the same amount as the order total.
 */
@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	/** The order page polls every 2 s; ask the provider at most this often per payment. */
	static final Duration MIN_CHECK_INTERVAL = Duration.ofSeconds(3);

	private final PaymentProviders providers;
	private final PaymentRepository payments;
	private final OrderService orders;
	private final String publicUrl;

	public PaymentService(PaymentProviders providers, PaymentRepository payments, OrderService orders,
			@Value("${app.public-url}") String publicUrl) {
		this.providers = providers;
		this.payments = payments;
		this.orders = orders;
		this.publicUrl = publicUrl.replaceAll("/+$", "");
	}

	/**
	 * Starts a payment for a new order, or returns the one already started (a retried checkout).
	 * If the provider refuses, the order is marked payment_failed and its stock is released.
	 */
	public Optional<Payment> startFor(CustomerOrder order) {
		Optional<Payment> existing = latestFor(order.id);
		if (existing.isPresent() || order.status != OrderStatus.PENDING_PAYMENT) {
			return existing;
		}
		PaymentProvider provider = providers.defaultProvider()
				.orElseThrow(() -> new IllegalStateException("No payment provider enabled (app.payments.enabled)"));
		try {
			String returnUrl = publicUrl + "/api/payments/" + provider.code() + "/return";
			PaymentProvider.Started started = provider
					.start(new PaymentProvider.Request(order.id, order.total, order.email, returnUrl));
			Payment payment = new Payment();
			payment.orderId = order.id;
			payment.providerCode = provider.code();
			payment.providerReference = started.providerReference();
			payment.redirectUrl = started.redirectUrl();
			payment.status = PaymentStatus.PENDING;
			payment.amount = order.total;
			payment.createdAt = Instant.now();
			log.info("Payment started for order {} at {}", order.id, provider.code());
			return Optional.of(payments.save(payment));
		} catch (PaymentException e) {
			log.error("Could not start payment for order {}: {}", order.id, e.getMessage());
			orders.markPaymentFailed(order.id);
			return Optional.empty();
		}
	}

	public Optional<Payment> latestFor(String orderId) {
		return payments.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
	}

	/** Called when the order page polls: asks the provider if the payment is still open. Throttled. */
	public void refreshIfPending(CustomerOrder order) {
		if (order.status != OrderStatus.PENDING_PAYMENT) {
			return;
		}
		latestFor(order.id)
				.filter(p -> p.status == PaymentStatus.PENDING)
				.filter(p -> p.lastCheckedAt == null
						|| p.lastCheckedAt.plus(MIN_CHECK_INTERVAL).isBefore(Instant.now()))
				.ifPresent(this::refresh);
	}

	/** Customer came back from, or the provider called us about, this payment. Returns its order id. */
	public Optional<String> refreshByReference(String providerCode, String providerReference) {
		Optional<Payment> payment = payments.findByProviderCodeAndProviderReference(providerCode, providerReference);
		payment.ifPresent(this::refresh);
		return payment.map(p -> p.orderId);
	}

	private void refresh(Payment payment) {
		PaymentProvider provider = providers.find(payment.providerCode).orElse(null);
		if (provider == null) {
			log.warn("Payment {} uses provider {} which is not enabled", payment.id, payment.providerCode);
			return;
		}
		PaymentProvider.Result result;
		try {
			result = provider.fetchStatus(payment.providerReference);
		} catch (PaymentException e) {
			log.warn("Could not check payment for order {}: {}", payment.orderId, e.getMessage());
			return;
		} finally {
			payment.lastCheckedAt = Instant.now();
		}
		payment.providerState = result.providerState();

		if (result.status() == PaymentStatus.PAID) {
			if (!payment.amount.equals(result.amount())) {
				// Never accept a payment for a different amount. Leave it for a human to look at.
				log.error("Order {} NOT marked paid: provider reports {} but the order total is {}", payment.orderId,
						result.amount(), payment.amount);
				payments.save(payment);
				return;
			}
			payment.status = PaymentStatus.PAID;
			payments.save(payment);
			orders.markPaid(payment.orderId);
		} else if (result.status() == PaymentStatus.FAILED) {
			payment.status = PaymentStatus.FAILED;
			payments.save(payment);
			orders.markPaymentFailed(payment.orderId);
		} else {
			payments.save(payment);
		}
	}
}
