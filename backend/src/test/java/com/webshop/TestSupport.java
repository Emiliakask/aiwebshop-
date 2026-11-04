package com.webshop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.webshop.auth.AdminOidcUserService;
import com.webshop.common.Money;
import com.webshop.products.Product;

public final class TestSupport {

	private TestSupport() {
	}

	/** A logged-in admin (Google account on the allow-list). */
	public static RequestPostProcessor admin() {
		return oidcLogin()
				.authorities(new SimpleGrantedAuthority(AdminOidcUserService.ADMIN_ROLE))
				.idToken(token -> token.claim("email", "admin@example.com").claim("name", "Shop Admin"));
	}

	/** Logged in with Google, but not an admin. */
	public static RequestPostProcessor someGoogleUser() {
		return oidcLogin().idToken(token -> token.claim("email", "stranger@example.com"));
	}

	public static Product product(long priceCents, int stock) {
		Product product = new Product();
		product.slug = "test-" + UUID.randomUUID();
		product.name = "Test product";
		product.description = "";
		product.category = "Test";
		product.price = Money.eur(priceCents);
		product.imageUrl = "https://example.com/p.jpg";
		product.imageAlt = "Test product";
		product.stock = stock;
		return product;
	}

	public static String productJson(String slug, String amount, String currency, String stock) {
		return """
				{"slug":"%s","name":"Mug","description":"","category":"Kitchen",
				 "price":{"amount":%s,"currency":"%s"},
				 "imageUrl":"https://example.com/mug.jpg","imageAlt":"White mug","stock":%s}
				""".formatted(slug, amount, currency, stock);
	}
}
