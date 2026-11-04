package com.webshop.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

	@Test
	void multipliesAndAddsExactlyInCents() {
		// 0.10 + 0.20 is 0.30000000000000004 in floating point; in cents it's exactly 30.
		assertThat(Money.eur(10).plus(Money.eur(20))).isEqualTo(Money.eur(30));
		assertThat(Money.eur(1999).times(3)).isEqualTo(Money.eur(5997));
		assertThat(Money.eur(333).times(3).plus(Money.eur(1))).isEqualTo(Money.eur(1000));
	}

	@Test
	void isAlwaysEuro() {
		assertThat(Money.eur(1).currency()).isEqualTo("EUR");
	}

	@Test
	void overflowThrowsInsteadOfWrapping() {
		assertThatThrownBy(() -> Money.eur(Long.MAX_VALUE).plus(Money.eur(1))).isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> Money.eur(Long.MAX_VALUE).times(2)).isInstanceOf(ArithmeticException.class);
	}
}
