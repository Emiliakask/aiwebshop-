package com.webshop.payments;

import java.time.Instant;
import java.util.UUID;

import com.webshop.common.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One payment attempt for an order. An order can have several (docs/payments.md). */
@Entity
@Table(name = "payments")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	public UUID id;

	@Column(nullable = false)
	public String orderId;

	@Column(nullable = false)
	public String providerCode;

	@Column(unique = true)
	public String providerReference;

	@Column(columnDefinition = "text")
	public String redirectUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	public PaymentStatus status;

	/** Last state name reported by the provider, for support/debugging. */
	public String providerState;

	@Embedded
	@AttributeOverride(name = "amount", column = @Column(name = "amount"))
	@AttributeOverride(name = "currency", column = @Column(name = "currency"))
	public Money amount;

	@Column(nullable = false)
	public Instant createdAt;

	public Instant lastCheckedAt;
}
