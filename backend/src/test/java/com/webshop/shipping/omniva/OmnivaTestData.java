package com.webshop.shipping.omniva;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;

/**
 * Real entries copied from https://www.omniva.ee/locations.json (src/test/resources/omniva), so tests
 * never download the live list. Parsed with Jackson, which also checks the JSON field mapping.
 */
public final class OmnivaTestData {

	/** Tallinna Akadeemia Coop Konsumi pakiautomaat, Akadeemia tee 35, Tallinn. */
	public static final String TALLINN_MACHINE = "96003";

	private OmnivaTestData() {
	}

	public static List<OmnivaLocation> sampleLocations() {
		try (InputStream in = OmnivaTestData.class.getResourceAsStream("/omniva/locations-sample.json")) {
			return Arrays.asList(JsonMapper.builder().build().readValue(in, OmnivaLocation[].class));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
