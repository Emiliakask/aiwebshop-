package com.webshop.cart;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Only product ids and quantities come in. Prices always come from the database. */
@RestController
@RequestMapping("/api/cart")
public class CartController {

	public record AddItem(@NotNull UUID productId, @NotNull Integer quantity) {
	}

	public record UpdateItem(@NotNull Integer quantity) {
	}

	private final CartService carts;

	public CartController(CartService carts) {
		this.carts = carts;
	}

	@GetMapping
	public CartView get(HttpSession session) {
		return carts.view(SessionCart.from(session));
	}

	@PostMapping("/items")
	public CartView add(@Valid @RequestBody AddItem body, HttpSession session) {
		SessionCart cart = SessionCart.from(session);
		carts.add(cart, body.productId(), body.quantity());
		cart.saveTo(session);
		return carts.view(cart);
	}

	@PatchMapping("/items/{productId}")
	public CartView update(@PathVariable UUID productId, @Valid @RequestBody UpdateItem body, HttpSession session) {
		SessionCart cart = SessionCart.from(session);
		carts.update(cart, productId, body.quantity());
		cart.saveTo(session);
		return carts.view(cart);
	}

	@DeleteMapping("/items/{productId}")
	public CartView remove(@PathVariable UUID productId, HttpSession session) {
		SessionCart cart = SessionCart.from(session);
		carts.remove(cart, productId);
		cart.saveTo(session);
		return carts.view(cart);
	}
}
