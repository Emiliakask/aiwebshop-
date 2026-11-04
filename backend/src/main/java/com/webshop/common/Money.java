package com.webshop.common;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * An amount in euro cents. The shop sells in EUR only.
 *
 * Never use floating point or division for money: use {@link #times} and {@link #plus}, which use
 * exact integer maths and throw on overflow instead of silently wrapping.
 */
@Embeddable
public record Money(
		@NotNull @Min(0) Long amount,
		@NotNull @Pattern(regexp = Money.EUR, message = "Only EUR is supported") String currency) {

	public static final String EUR = "EUR";

	public static Money eur(long cents) {
		return new Money(cents, EUR);
	}

	public Money times(int quantity) {
		return eur(Math.multiplyExact(amount, (long) quantity));
	}

	public Money plus(Money other) {
		return eur(Math.addExact(amount, other.amount));
	}
}
