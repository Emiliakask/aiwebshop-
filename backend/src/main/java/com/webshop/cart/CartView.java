package com.webshop.cart;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.webshop.common.Money;

/** Same shape as cartSchema in frontend/src/features/cart/types.ts. */
public record CartView(List<Item> items, int itemCount, Money subtotal) {

	public record Item(UUID productId, String slug, String name, String imageUrl, String imageAlt, Money unitPrice,
			int quantity, Money lineTotal, int stock, Issue issue) {
	}

	public enum Issue {
		@JsonProperty("out_of_stock")
		OUT_OF_STOCK,
		@JsonProperty("insufficient_stock")
		INSUFFICIENT_STOCK,
		@JsonProperty("price_changed")
		PRICE_CHANGED;

		/** Out of stock and too little stock stop checkout; a price change is only a notice. */
		public boolean blocksCheckout() {
			return this != PRICE_CHANGED;
		}
	}
}
