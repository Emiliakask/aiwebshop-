package com.webshop.shipping;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.webshop.cart.CartService;
import com.webshop.cart.SessionCart;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

	private final ShippingService shipping;
	private final CartService carts;

	public ShippingController(ShippingService shipping, CartService carts) {
		this.shipping = shipping;
		this.carts = carts;
	}

	/** Delivery options for a country, priced for this session's cart (free from €50). */
	@GetMapping("/methods")
	public List<ShippingService.ShippingMethod> methods(@RequestParam String country, HttpSession session) {
		return shipping.methods(country, carts.view(SessionCart.from(session)).subtotal());
	}

	@GetMapping("/pickup-points")
	public List<PickupPoint> pickupPoints(@RequestParam String carrier, @RequestParam String country) {
		return shipping.pickupPoints(carrier, country);
	}
}
