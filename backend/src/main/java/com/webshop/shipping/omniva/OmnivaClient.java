package com.webshop.shipping.omniva;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Downloads Omniva's public location list. Kept separate so tests can replace it. */
@Component
public class OmnivaClient {

	private final RestClient http;
	private final String feedUrl;

	public OmnivaClient(@Value("${app.shipping.omniva.locations-url}") String feedUrl) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		requestFactory.setReadTimeout(Duration.ofSeconds(20));
		this.http = RestClient.builder().requestFactory(requestFactory).build();
		this.feedUrl = feedUrl;
	}

	public List<OmnivaLocation> fetchLocations() {
		OmnivaLocation[] locations = http.get().uri(feedUrl).retrieve().body(OmnivaLocation[].class);
		return locations == null ? List.of() : Arrays.asList(locations);
	}
}
