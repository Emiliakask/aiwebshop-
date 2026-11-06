package com.webshop.shipping;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.webshop.common.Money;

/** Picks the enabled carriers and decides delivery prices. The browser never sends a price. */
@Service
public class ShippingService {

	/** A delivery option at checkout, priced for the current cart. */
	public record ShippingMethod(String carrierCode, String name, Money price) {
	}

	private final Map<String, ShippingCarrier> enabled;
	private final long freeFromCents;

	public ShippingService(List<ShippingCarrier> carriers, @Value("${app.shipping.enabled}") List<String> enabledCodes,
			@Value("${app.shipping.free-from-cents}") long freeFromCents) {
		Map<String, ShippingCarrier> byCode = carriers.stream()
				.collect(Collectors.toMap(ShippingCarrier::code, Function.identity()));
		// Keep the configured order: it is the order shown at checkout.
		this.enabled = enabledCodes.stream().map(String::trim).filter(byCode::containsKey)
				.collect(Collectors.toMap(Function.identity(), byCode::get, (a, b) -> a, java.util.LinkedHashMap::new));
		this.freeFromCents = freeFromCents;
	}

	public List<ShippingMethod> methods(String countryCode, Money subtotal) {
		return enabled.values().stream()
				.filter(carrier -> carrier.countries().contains(countryCode))
				.map(carrier -> new ShippingMethod(carrier.code(), carrier.displayName(), price(carrier, subtotal)))
				.toList();
	}

	public List<PickupPoint> pickupPoints(String carrierCode, String countryCode) {
		ShippingCarrier carrier = requireCarrier(carrierCode);
		if (!carrier.countries().contains(countryCode)) {
			return List.of();
		}
		return carrier.pickupPoints(countryCode).stream()
				.sorted(Comparator.comparing(PickupPoint::city).thenComparing(PickupPoint::name))
				.toList();
	}

	/** The chosen machine, if it really exists for that carrier and country. */
	public Optional<PickupPoint> findPickupPoint(String carrierCode, String countryCode, String pickupPointId) {
		return Optional.ofNullable(enabled.get(carrierCode))
				.filter(carrier -> carrier.countries().contains(countryCode))
				.flatMap(carrier -> carrier.pickupPoints(countryCode).stream()
						.filter(point -> point.id().equals(pickupPointId))
						.findFirst());
	}

	public Money price(String carrierCode, Money subtotal) {
		return price(requireCarrier(carrierCode), subtotal);
	}

	public String carrierName(String carrierCode) {
		return requireCarrier(carrierCode).displayName();
	}

	private Money price(ShippingCarrier carrier, Money subtotal) {
		return subtotal.amount() >= freeFromCents ? Money.eur(0) : carrier.basePrice();
	}

	private ShippingCarrier requireCarrier(String carrierCode) {
		ShippingCarrier carrier = enabled.get(carrierCode);
		if (carrier == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown delivery method");
		}
		return carrier;
	}
}
