package com.webshop.orders;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.webshop.cart.SessionCart;
import com.webshop.common.Money;
import com.webshop.products.Product;
import com.webshop.products.ProductRepository;

@Service
public class OrderService {

	/** €4.99 delivery, free from €50.00. Will move to the shipping carriers (docs/shipping.md). */
	static final Money SHIPPING_FEE = Money.eur(499);
	static final long FREE_SHIPPING_FROM = 5000;

	private final OrderRepository orders;
	private final ProductRepository products;
	private final SecureRandom random = new SecureRandom();

	public OrderService(OrderRepository orders, ProductRepository products) {
		this.orders = orders;
		this.products = products;
	}

	public Optional<CustomerOrder> findByIdempotencyKey(String key) {
		return orders.findByIdempotencyKey(key);
	}

	public Optional<CustomerOrder> find(String id) {
		return orders.findById(id);
	}

	/**
	 * Creates an order from the cart. Prices come from the database, stock is checked and taken with
	 * the product rows locked, and everything is integer cents. Refuses if anything is out of stock.
	 */
	@Transactional
	public CustomerOrder place(SessionCart cart, CheckoutRequest request, String idempotencyKey) {
		Optional<CustomerOrder> existing = orders.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return existing.get();
		}
		if (cart.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
		}

		Map<UUID, Product> locked = products.findAllByIdForUpdate(cart.lines().keySet()).stream()
				.collect(Collectors.toMap(p -> p.id, Function.identity()));

		CustomerOrder order = new CustomerOrder();
		Money subtotal = Money.eur(0);
		for (Map.Entry<UUID, SessionCart.Line> entry : cart.lines().entrySet()) {
			Product product = locked.get(entry.getKey());
			int quantity = entry.getValue().quantity();
			if (product == null || product.stock <= 0 || quantity > product.stock) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Some items are no longer available");
			}
			OrderLine line = new OrderLine(product.id, product.name, product.price, quantity);
			order.lines.add(line);
			subtotal = subtotal.plus(line.lineTotal);
			product.stock -= quantity;
		}

		Money shipping = shippingFor(subtotal);
		order.id = newOrderId();
		order.status = OrderStatus.PENDING_PAYMENT;
		order.idempotencyKey = idempotencyKey;
		order.email = request.email().trim();
		order.fullName = request.fullName().trim();
		order.addressLine1 = request.addressLine1().trim();
		order.city = request.city().trim();
		order.postalCode = request.postalCode().trim();
		order.country = request.country();
		order.subtotal = subtotal;
		order.shipping = shipping;
		order.total = subtotal.plus(shipping);
		order.createdAt = Instant.now();
		return orders.save(order);
	}

	static Money shippingFor(Money subtotal) {
		return subtotal.amount() >= FREE_SHIPPING_FROM ? Money.eur(0) : SHIPPING_FEE;
	}

	private String newOrderId() {
		String id;
		do {
			id = "WS-" + (100000 + random.nextInt(900000));
		} while (orders.existsById(id));
		return id;
	}
}
