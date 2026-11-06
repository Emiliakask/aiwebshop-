package com.webshop.payments.everypay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.webshop.common.Money;
import com.webshop.payments.PaymentException;
import com.webshop.payments.PaymentStatus;

class EveryPayProviderTest {

	@Test
	void amountsConvertExactlyBothWays() {
		assertThat(EveryPayProvider.toEuros(Money.eur(1234))).isEqualByComparingTo("12.34");
		assertThat(EveryPayProvider.toEuros(Money.eur(8900)).toPlainString()).isEqualTo("89.00");
		assertThat(EveryPayProvider.toEuros(Money.eur(1)).toPlainString()).isEqualTo("0.01");
		assertThat(EveryPayProvider.toCents(new BigDecimal("12.34"))).isEqualTo(1234);
		assertThat(EveryPayProvider.toCents(new BigDecimal("124900.0"))).isEqualTo(12490000);
		assertThatThrownBy(() -> EveryPayProvider.toCents(new BigDecimal("12.345")))
				.isInstanceOf(PaymentException.class);
	}

	@Test
	void statesMapToOurStatuses() {
		assertThat(EveryPayProvider.map("settled")).isEqualTo(PaymentStatus.PAID);
		assertThat(EveryPayProvider.map("authorised")).isEqualTo(PaymentStatus.PAID);
		assertThat(EveryPayProvider.map("failed")).isEqualTo(PaymentStatus.FAILED);
		assertThat(EveryPayProvider.map("abandoned")).isEqualTo(PaymentStatus.FAILED);
		assertThat(EveryPayProvider.map("voided")).isEqualTo(PaymentStatus.FAILED);
		assertThat(EveryPayProvider.map("initial")).isEqualTo(PaymentStatus.PENDING);
		assertThat(EveryPayProvider.map("waiting_for_3ds_response")).isEqualTo(PaymentStatus.PENDING);
		assertThat(EveryPayProvider.map("sent_for_processing")).isEqualTo(PaymentStatus.PENDING);
	}

	@Test
	void chargedAmountComesFromOurOwnEurAccountOnly() {
		EveryPayProvider provider = new EveryPayProvider("https://igw-demo.every-pay.com/api/v4", "u", "s", "EUR3D1",
				"en");
		// Real status responses carry initial_amount and account_name, but no currency.
		assertThat(provider.amount(new EveryPayProvider.StatusResponse("r", "settled", new BigDecimal("199.0"), "EUR3D1",
				null))).isEqualTo(Money.eur(19900));
		assertThat(provider.amount(new EveryPayProvider.StatusResponse("r", "settled", new BigDecimal("199.0"),
				"OTHER", null))).isNull();
		assertThat(provider.amount(new EveryPayProvider.StatusResponse("r", "settled", null, "EUR3D1", null)))
				.isNull();
	}

	@Test
	void refusesToStartWithoutCredentials() {
		EveryPayProvider unconfigured = new EveryPayProvider("https://igw-demo.every-pay.com/api/v4", "", "", "EUR3D1",
				"en");
		assertThatThrownBy(() -> unconfigured.fetchStatus("x")).hasMessageContaining("EVERYPAY_API_USERNAME");
	}
}
