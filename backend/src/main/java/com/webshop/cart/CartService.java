package com.webshop.cart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.webshop.common.Money;
import com.webshop.products.Product;
import com.webshop.products.ProductRepository;

@Service
public class CartService {

	/** Most items one cart line can hold, regardless of stock. Same as MAX_QUANTITY_PER_ITEM in the frontend. */
	public static final int MAX_QUANTITY_PER_ITEM = 10;

	private final ProductRepository products;

	public CartService(ProductRepository products) {
		this.products = products;
	}

	public void add(SessionCart cart, UUID productId, int quantity) {
		requireValidQuantity(quantity);
		Product product = products.findById(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
		if (product.stock <= 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is out of stock");
		}
		SessionCart.Line existing = cart.get(productId);
		int wanted = (existing == null ? 0 : existing.quantity()) + quantity;
		int capped = Math.min(Math.min(wanted, product.stock), MAX_QUANTITY_PER_ITEM);
		cart.put(productId, new SessionCart.Line(capped, product.price.amount()));
	}

	public void update(SessionCart cart, UUID productId, int quantity) {
		if (cart.get(productId) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart");
		}
		requireValidQuantity(quantity);
		long currentPrice = products.findById(productId).map(p -> p.price.amount())
				.orElse(cart.get(productId).priceWhenAdded());
		cart.put(productId, new SessionCart.Line(quantity, currentPrice));
	}

	public void remove(SessionCart cart, UUID productId) {
		cart.remove(productId);
	}

	/** The cart with current prices, stock and problems. Products deleted since are left out. */
	public CartView view(SessionCart cart) {
		Map<UUID, Product> byId = products.findAllById(cart.lines().keySet()).stream()
				.collect(Collectors.toMap(p -> p.id, Function.identity()));

		List<CartView.Item> items = new ArrayList<>();
		Money subtotal = Money.eur(0);
		int itemCount = 0;
		for (Map.Entry<UUID, SessionCart.Line> entry : cart.lines().entrySet()) {
			Product product = byId.get(entry.getKey());
			if (product == null) {
				continue;
			}
			int quantity = entry.getValue().quantity();
			Money lineTotal = product.price.times(quantity);
			items.add(new CartView.Item(product.id, product.slug, product.name, product.imageUrl, product.imageAlt,
					product.price, quantity, lineTotal, product.stock, issueOf(product, entry.getValue())));
			subtotal = subtotal.plus(lineTotal);
			itemCount += quantity;
		}
		return new CartView(items, itemCount, subtotal);
	}

	static CartView.Issue issueOf(Product product, SessionCart.Line line) {
		if (product.stock <= 0) {
			return CartView.Issue.OUT_OF_STOCK;
		}
		if (line.quantity() > product.stock) {
			return CartView.Issue.INSUFFICIENT_STOCK;
		}
		if (line.priceWhenAdded() != product.price.amount()) {
			return CartView.Issue.PRICE_CHANGED;
		}
		return null;
	}

	private static void requireValidQuantity(int quantity) {
		if (quantity < 1 || quantity > MAX_QUANTITY_PER_ITEM) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid quantity");
		}
	}
}
