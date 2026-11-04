package com.webshop.orders;

import java.util.UUID;

import com.webshop.common.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

/** A snapshot of one product at order time, so later product edits don't change past orders. */
@Embeddable
public class OrderLine {

	public UUID productId;

	public String name;

	@Embedded
	@AttributeOverride(name = "amount", column = @Column(name = "unit_price_amount"))
	@AttributeOverride(name = "currency", column = @Column(name = "unit_price_currency"))
	public Money unitPrice;

	public int quantity;

	@Embedded
	@AttributeOverride(name = "amount", column = @Column(name = "line_total_amount"))
	@AttributeOverride(name = "currency", column = @Column(name = "line_total_currency"))
	public Money lineTotal;

	protected OrderLine() {
	}

	public OrderLine(UUID productId, String name, Money unitPrice, int quantity) {
		this.productId = productId;
		this.name = name;
		this.unitPrice = unitPrice;
		this.quantity = quantity;
		this.lineTotal = unitPrice.times(quantity);
	}
}
