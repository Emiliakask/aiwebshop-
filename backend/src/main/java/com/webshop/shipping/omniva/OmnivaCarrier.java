package com.webshop.shipping.omniva;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.webshop.common.Money;
import com.webshop.shipping.PickupPoint;
import com.webshop.shipping.ShippingCarrier;

/**
 * Omniva parcel machines (pakiautomaadid) in Estonia, Latvia and Lithuania.
 *
 * The public list is downloaded on first use and refreshed after {@code app.shipping.omniva.refresh}.
 * If a refresh fails, the previous list keeps being used, so an Omniva outage doesn't stop checkout.
 */
@Component
public class OmnivaCarrier implements ShippingCarrier {

	public static final String CODE = "omniva";

	private static final Logger log = LoggerFactory.getLogger(OmnivaCarrier.class);
	private static final Set<String> COUNTRIES = Set.of("EE", "LV", "LT");
	private static final String PARCEL_MACHINE = "0";

	private final OmnivaClient client;
	private final Money price;
	private final Duration refreshAfter;
	private final Clock clock;

	private volatile Map<String, List<PickupPoint>> byCountry;
	private volatile Instant loadedAt;

	@Autowired
	public OmnivaCarrier(OmnivaClient client, @Value("${app.shipping.omniva.price-cents}") long priceCents,
			@Value("${app.shipping.omniva.refresh}") Duration refreshAfter) {
		this(client, Money.eur(priceCents), refreshAfter, Clock.systemUTC());
	}

	OmnivaCarrier(OmnivaClient client, Money price, Duration refreshAfter, Clock clock) {
		this.client = client;
		this.price = price;
		this.refreshAfter = refreshAfter;
		this.clock = clock;
	}

	@Override
	public String code() {
		return CODE;
	}

	@Override
	public String displayName() {
		return "Omniva parcel machine";
	}

	@Override
	public Set<String> countries() {
		return COUNTRIES;
	}

	@Override
	public Money basePrice() {
		return price;
	}

	@Override
	public List<PickupPoint> pickupPoints(String countryCode) {
		return loaded().getOrDefault(countryCode, List.of());
	}

	private Map<String, List<PickupPoint>> loaded() {
		Map<String, List<PickupPoint>> current = byCountry;
		if (current != null && clock.instant().isBefore(loadedAt.plus(refreshAfter))) {
			return current;
		}
		synchronized (this) {
			if (byCountry != null && clock.instant().isBefore(loadedAt.plus(refreshAfter))) {
				return byCountry;
			}
			try {
				byCountry = toPickupPoints(client.fetchLocations()).stream()
						.collect(Collectors.groupingBy(PickupPoint::countryCode));
				loadedAt = clock.instant();
				log.info("Loaded Omniva parcel machines: {}", byCountry.entrySet().stream()
						.map(e -> e.getKey() + "=" + e.getValue().size()).sorted().toList());
			} catch (RuntimeException e) {
				if (byCountry == null) {
					log.error("Could not load Omniva parcel machines", e);
					throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
							"Parcel machines are temporarily unavailable");
				}
				log.warn("Could not refresh Omniva parcel machines, using the previous list", e);
				loadedAt = clock.instant(); // don't retry on every request
			}
			return byCountry;
		}
	}

	/**
	 * Keeps public parcel machines only: no post offices, no virtual entries without a location
	 * ("1. eelistus", "2. Picapac": city and street are just the name again) and no company-internal
	 * machine ("ettevõttesisene").
	 */
	static List<PickupPoint> toPickupPoints(List<OmnivaLocation> locations) {
		return locations.stream()
				.filter(l -> PARCEL_MACHINE.equals(l.type()))
				.filter(l -> COUNTRIES.contains(l.country()))
				.filter(l -> notBlank(l.zip()) && notBlank(l.name()) && notBlank(l.a1()))
				.filter(l -> !(notBlank(l.a2()) && l.a2().equals(l.street())))
				.filter(l -> !l.name().toLowerCase(java.util.Locale.ROOT).contains("ettevõttesisene"))
				.map(l -> new PickupPoint(l.zip().trim(), CODE, l.name().trim(), address(l), city(l), l.country(),
						coordinate(l.latitude()), coordinate(l.longitude())))
				.toList();
	}

	/** Estonia: A2 is the city (A3 is a district). Latvia/Lithuania: A3 is the town when present. */
	static String city(OmnivaLocation l) {
		String city = "EE".equals(l.country()) ? firstNotBlank(l.a2(), l.a1())
				: firstNotBlank(l.a3(), l.a2(), l.a1());
		return city.trim();
	}

	/** Street and number; village machines often have no street, so the village name is used instead. */
	static String address(OmnivaLocation l) {
		String street = l.street() == null ? "" : l.street().trim();
		String number = l.houseNumber() == null ? "" : l.houseNumber().trim();
		String address = (street + " " + number).trim();
		if (address.isEmpty() && notBlank(l.a3()) && !l.a3().trim().equals(city(l))) {
			return l.a3().trim();
		}
		return address;
	}

	private static Double coordinate(String value) {
		try {
			return notBlank(value) ? Double.valueOf(value.trim()) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String firstNotBlank(String... values) {
		for (String value : values) {
			if (notBlank(value)) {
				return value;
			}
		}
		return "";
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}
