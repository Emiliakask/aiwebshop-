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

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	/** Same shape as orderSchema in frontend/src/features/checkout/types.ts. */
	public record OrderView(String id, OrderStatus status, String email, Money total, String paymentUrl) {
		static OrderView of(CustomerOrder order) {
			// paymentUrl will come from the payment provider (docs/payments.md).
			return new OrderView(order.id, order.status, order.email, order.total, null);
		}
	}

	private static final String MY_ORDERS = OrderController.class.getName() + ".orders";

	private final OrderService orders;

	public OrderController(OrderService orders) {
		this.orders = orders;
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
		return ResponseEntity.status(retry ? HttpStatus.OK : HttpStatus.CREATED).body(OrderView.of(order));
	}

	/** Only the browser session that placed the order, or an admin, may see it. Others get 404. */
	@GetMapping("/{id}")
	public OrderView get(@PathVariable String id, HttpSession session, Authentication authentication) {
		boolean allowed = myOrders(session).contains(id) || MeController.isAdmin(authentication);
		return orders.find(id).filter(order -> allowed).map(OrderView::of)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
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
