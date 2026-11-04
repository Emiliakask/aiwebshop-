package com.webshop.cart;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

/**
 * The shopper's cart, kept in the server-side session (guest or logged in). It stores only product
 * ids, quantities and the price seen when the item was added; current prices and stock always come
 * from the database.
 */
public class SessionCart implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final String SESSION_KEY = SessionCart.class.getName();

	public record Line(int quantity, long priceWhenAdded) implements Serializable {
	}

	private final Map<UUID, Line> lines = new LinkedHashMap<>();

	public static SessionCart from(HttpSession session) {
		if (session.getAttribute(SESSION_KEY) instanceof SessionCart cart) {
			return cart;
		}
		SessionCart cart = new SessionCart();
		session.setAttribute(SESSION_KEY, cart);
		return cart;
	}

	/** Re-store after a change, so session stores that copy attributes see it. */
	public void saveTo(HttpSession session) {
		session.setAttribute(SESSION_KEY, this);
	}

	/** Lines in the order they were added. */
	public Map<UUID, Line> lines() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(lines));
	}

	public Line get(UUID productId) {
		return lines.get(productId);
	}

	public void put(UUID productId, Line line) {
		lines.put(productId, line);
	}

	public void remove(UUID productId) {
		lines.remove(productId);
	}

	public void clear() {
		lines.clear();
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}
}
