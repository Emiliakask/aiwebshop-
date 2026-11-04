package com.webshop.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.webshop.common.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** A placed order. Named CustomerOrder because ORDER is a reserved word in SQL. */
@Entity
@Table(name = "orders")
public class CustomerOrder {

	/** Human-readable number shown to the customer, e.g. WS-123456. */
	@Id
	public String id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	public OrderStatus status;

	/** Retries of the same checkout send the same key and get this same order back. */
	@Column(nullable = false, unique = true)
	public String idempotencyKey;

	@Column(nullable = false)
	public String email;

	@Column(nullable = false)
	public String fullName;

	@Column(nullable = false)
	public String addressLine1;

	@Column(nullable = false)
	public String city;

	@Column(nullable = false)
	public String postalCode;

	@Column(nullable = false)
	public String country;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
	@OrderColumn(name = "position")
	public List<OrderLine> lines = new ArrayList<>();

	@Embedded
	@AttributeOverride(name = "amount", column = @Column(name = "subtotal_amount"))
	@AttributeOverride(name = "currency", column = @Column(name = "subtotal_currency"))
	public Money subtotal;

	@Embedded
	@AttributeOverride(name = "amount", column = @Column(name = "shipping_amount"))
	@AttributeOverride(name = "currency", column = @Column(name = "shipping_currency"))
	public Money shipping;

	@Embedded
	@AttributeOverride(name = "amount", column = @Column(name = "total_amount"))
	@AttributeOverride(name = "currency", column = @Column(name = "total_currency"))
	public Money total;

	@Column(nullable = false)
	public Instant createdAt;
}
