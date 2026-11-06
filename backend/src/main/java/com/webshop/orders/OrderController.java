package com.webshop.orders;

import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.webshop.auth.MeController;
import com.webshop.cart.SessionCart;
import com.webshop.common.Money;
import com.webshop.payments.PaymentService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	/** Same shape as orderSchema in frontend/src/features/checkout/types.ts. */
	public record OrderView(String id, OrderStatus status, String email, Money total, Money shipping,
			Delivery delivery, String paymentUrl) {
		/** paymentUrl: the provider's payment page, only while the order still waits for payment. */
		static OrderView of(CustomerOrder order, String paymentUrl) {
			// Orders placed before parcel machine delivery existed have no delivery info.
			Delivery delivery = order.carrierCode == null ? null
					: new Delivery(order.carrierCode, order.pickupPointName, order.pickupPointAddress,
							order.pickupPointCity);
			return new OrderView(order.id, order.status, order.email, order.total,
					order.shipping == null ? Money.eur(0) : order.shipping, delivery,
					order.status == OrderStatus.PENDING_PAYMENT ? paymentUrl : null);
		}
	}

	/** Where the parcel goes. */
	public record Delivery(String carrierCode, String pickupPointName, String address, String city) {
	}

	private static final String MY_ORDERS = OrderController.class.getName() + ".orders";

	private final OrderService orders;
	private final PaymentService payments;

	public OrderController(OrderService orders, PaymentService payments) {
		this.orders = orders;
		this.payments = payments;
	}

	@PostMapping
	public ResponseEntity<OrderView> place(@RequestHeader(name = "Idempotency-Key", required = false) String key,
			@Valid @RequestBody CheckoutRequest request, HttpSession session) {
		if (key == null || key.isBlank() || key.length() > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Idempotency-Key header");
		}
		boolean retry = orders.findByIdempotencyKey(key).isPresent();
		SessionCart cart = SessionCart.from(session);
		CustomerOrder order = orders.place(cart, request, key);
		if (!retry) {
			cart.clear();
			cart.saveTo(session);
		}
		rememberOrder(session, order.id);
		// The order is saved (stock reserved) before we talk to the payment provider.
		String paymentUrl = payments.startFor(order).map(p -> p.redirectUrl).orElse(null);
		CustomerOrder current = orders.find(order.id).orElse(order);
		return ResponseEntity.status(retry ? HttpStatus.OK : HttpStatus.CREATED).body(OrderView.of(current, paymentUrl));
	}

	/** Only the browser session that placed the order, or an admin, may see it. Others get 404. */
	@GetMapping("/{id}")
	public OrderView get(@PathVariable String id, HttpSession session, Authentication authentication) {
		boolean allowed = myOrders(session).contains(id) || MeController.isAdmin(authentication);
		CustomerOrder order = orders.find(id).filter(o -> allowed)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		// The provider's callback can't reach a local machine, so the polling order page also asks the provider.
		payments.refreshIfPending(order);
		CustomerOrder current = orders.find(id).orElse(order);
		return OrderView.of(current, payments.latestFor(id).map(p -> p.redirectUrl).orElse(null));
	}

	@SuppressWarnings("unchecked")
	private static Set<String> myOrders(HttpSession session) {
		Object value = session.getAttribute(MY_ORDERS);
		return value instanceof Set<?> set ? (Set<String>) set : Set.of();
	}

	private static void rememberOrder(HttpSession session, String orderId) {
		Set<String> ids = new HashSet<>(myOrders(session));
		ids.add(orderId);
		session.setAttribute(MY_ORDERS, ids);
	}
}
