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
import com.webshop.shipping.PickupPoint;
import com.webshop.shipping.ShippingService;

@Service
public class OrderService {

	private final OrderRepository orders;
	private final ProductRepository products;
	private final ShippingService shipping;
	private final SecureRandom random = new SecureRandom();

	public OrderService(OrderRepository orders, ProductRepository products, ShippingService shipping) {
		this.orders = orders;
		this.products = products;
		this.shipping = shipping;
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
		PickupPoint pickupPoint = shipping
				.findPickupPoint(request.carrierCode(), request.country(), request.pickupPointId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown parcel machine"));

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

		Money deliveryPrice = shipping.price(pickupPoint.carrierCode(), subtotal);
		order.id = newOrderId();
		order.status = OrderStatus.PENDING_PAYMENT;
		order.idempotencyKey = idempotencyKey;
		order.email = request.email().trim();
		order.fullName = request.fullName().trim();
		order.phone = request.phone().trim();
		order.country = request.country();
		order.carrierCode = pickupPoint.carrierCode();
		order.pickupPointId = pickupPoint.id();
		order.pickupPointName = pickupPoint.name();
		order.pickupPointAddress = pickupPoint.address();
		order.pickupPointCity = pickupPoint.city();
		order.subtotal = subtotal;
		order.shipping = deliveryPrice;
		order.total = subtotal.plus(deliveryPrice);
		order.createdAt = Instant.now();
		return orders.save(order);
	}

	/** pending_payment -> paid. Does nothing if the order already left pending_payment. */
	@Transactional
	public void markPaid(String orderId) {
		orders.findByIdForUpdate(orderId).filter(o -> o.status == OrderStatus.PENDING_PAYMENT).ifPresent(order -> {
			order.status = OrderStatus.PAID;
			order.paidAt = Instant.now();
		});
	}

	/** pending_payment -> payment_failed, and the reserved stock goes back on sale. Happens at most once. */
	@Transactional
	public void markPaymentFailed(String orderId) {
		orders.findByIdForUpdate(orderId).filter(o -> o.status == OrderStatus.PENDING_PAYMENT).ifPresent(order -> {
			order.status = OrderStatus.PAYMENT_FAILED;
			Map<UUID, Product> locked = products
					.findAllByIdForUpdate(order.lines.stream().map(line -> line.productId).toList()).stream()
					.collect(Collectors.toMap(p -> p.id, Function.identity()));
			for (OrderLine line : order.lines) {
				Product product = locked.get(line.productId);
				if (product != null) {
					product.stock += line.quantity;
				}
			}
		});
	}

	private String newOrderId() {
		String id;
		do {
			id = "WS-" + (100000 + random.nextInt(900000));
		} while (orders.existsById(id));
		return id;
	}
}
