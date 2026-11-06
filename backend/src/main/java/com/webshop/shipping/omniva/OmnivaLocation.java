package com.webshop.shipping.omniva;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry of https://www.omniva.ee/locations.json. A0..A8 are address levels: A0 country,
 * A1 county, A2 municipality/city, A3 district/settlement, A5 street, A7 house number.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OmnivaLocation(
		@JsonProperty("ZIP") String zip,
		@JsonProperty("NAME") String name,
		/** "0" = parcel machine, "1" = post office. */
		@JsonProperty("TYPE") String type,
		@JsonProperty("A0_NAME") String country,
		@JsonProperty("A1_NAME") String a1,
		@JsonProperty("A2_NAME") String a2,
		@JsonProperty("A3_NAME") String a3,
		@JsonProperty("A5_NAME") String street,
		@JsonProperty("A7_NAME") String houseNumber,
		@JsonProperty("X_COORDINATE") String longitude,
		@JsonProperty("Y_COORDINATE") String latitude) {
}
