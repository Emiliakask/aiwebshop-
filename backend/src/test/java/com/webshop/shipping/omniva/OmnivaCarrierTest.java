package com.webshop.shipping.omniva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.webshop.common.Money;
import com.webshop.shipping.PickupPoint;

class OmnivaCarrierTest {

	@Test
	void keepsOnlyPublicParcelMachines() {
		List<PickupPoint> points = OmnivaCarrier.toPickupPoints(OmnivaTestData.sampleLocations());

		assertThat(points).extracting(PickupPoint::id)
				.containsExactlyInAnyOrder("96003", "96338", "9111", "55583", "96401")
				// post office, virtual "1. eelistus" + "2. Picapac" entries, company-internal machine
				.doesNotContain("11701", "96331", "96440", "96419");
	}

	@Test
	void mapsNamesAddressesAndCitiesPerCountry() {
		List<PickupPoint> points = OmnivaCarrier.toPickupPoints(OmnivaTestData.sampleLocations());

		assertThat(find(points, "96003")).isEqualTo(new PickupPoint("96003", "omniva",
				"Tallinna Akadeemia Coop Konsumi pakiautomaat", "Akadeemia tee 35", "Tallinn", "EE",
				find(points, "96003").latitude(), find(points, "96003").longitude()));
		assertThat(find(points, "96003").latitude()).isBetween(59.0, 60.0); // Y is latitude, X longitude
		assertThat(find(points, "9111").city()).isEqualTo("Rīga");
		assertThat(find(points, "55583").city()).isEqualTo("Akmenės m.");
		// Village machine without a street: the village is shown as the address.
		assertThat(find(points, "96401").address()).isEqualTo("Ala küla");
	}

	@Test
	void downloadsOnceAndReusesTheListUntilRefreshTime() {
		OmnivaClient client = mock(OmnivaClient.class);
		when(client.fetchLocations()).thenReturn(OmnivaTestData.sampleLocations());
		MutableClock clock = new MutableClock();
		OmnivaCarrier carrier = new OmnivaCarrier(client, Money.eur(499), Duration.ofHours(12), clock);

		assertThat(carrier.pickupPoints("EE")).hasSize(3);
		assertThat(carrier.pickupPoints("LV")).hasSize(1);
		verify(client, times(1)).fetchLocations();

		clock.advance(Duration.ofHours(13));
		carrier.pickupPoints("EE");
		verify(client, times(2)).fetchLocations();
	}

	@Test
	void keepsThePreviousListWhenRefreshFails() {
		OmnivaClient client = mock(OmnivaClient.class);
		when(client.fetchLocations()).thenReturn(OmnivaTestData.sampleLocations())
				.thenThrow(new IllegalStateException("Omniva is down"));
		MutableClock clock = new MutableClock();
		OmnivaCarrier carrier = new OmnivaCarrier(client, Money.eur(499), Duration.ofHours(12), clock);

		carrier.pickupPoints("EE");
		clock.advance(Duration.ofHours(13));

		assertThat(carrier.pickupPoints("EE")).hasSize(3);
	}

	@Test
	void saysUnavailableWhenTheListWasNeverLoaded() {
		OmnivaClient client = mock(OmnivaClient.class);
		when(client.fetchLocations()).thenThrow(new IllegalStateException("Omniva is down"));
		OmnivaCarrier carrier = new OmnivaCarrier(client, Money.eur(499), Duration.ofHours(12), new MutableClock());

		assertThatThrownBy(() -> carrier.pickupPoints("EE")).isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("503");
	}

	private static PickupPoint find(List<PickupPoint> points, String id) {
		return points.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
	}

	private static final class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-11-04T10:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}
}
