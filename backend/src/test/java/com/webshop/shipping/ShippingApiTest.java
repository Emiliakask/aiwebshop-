package com.webshop.shipping;

import static com.webshop.TestSupport.product;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.webshop.products.Product;
import com.webshop.products.ProductRepository;
import com.webshop.shipping.omniva.OmnivaClient;
import com.webshop.shipping.omniva.OmnivaTestData;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShippingApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ProductRepository products;

	@MockitoBean
	OmnivaClient omniva;

	final MockHttpSession session = new MockHttpSession();

	@BeforeEach
	void omnivaList() {
		when(omniva.fetchLocations()).thenReturn(OmnivaTestData.sampleLocations());
	}

	@Test
	void omnivaCostsFourNinetyNineBelowFiftyEuros() throws Exception {
		mvc.perform(get("/api/shipping/methods").param("country", "EE").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].carrierCode").value("omniva"))
				.andExpect(jsonPath("$[0].price.amount").value(499))
				.andExpect(jsonPath("$[0].price.currency").value("EUR"));
	}

	@Test
	void deliveryIsFreeFromFiftyEurosInTheCart() throws Exception {
		Product lamp = products.save(product(5000, 3));
		mvc.perform(post("/api/cart/items").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":\"%s\",\"quantity\":1}".formatted(lamp.id)));

		mvc.perform(get("/api/shipping/methods").param("country", "EE").session(session))
				.andExpect(jsonPath("$[0].price.amount").value(0));
	}

	@Test
	void noMethodsForCountriesOmnivaDoesNotServe() throws Exception {
		mvc.perform(get("/api/shipping/methods").param("country", "SE").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void listsParcelMachinesSortedByCityThenName() throws Exception {
		mvc.perform(get("/api/shipping/pickup-points").param("carrier", "omniva").param("country", "EE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].city", contains("Tallinn", "Tartu linn", "Tõrva vald")))
				.andExpect(jsonPath("$[0].name").value("Tallinna Akadeemia Coop Konsumi pakiautomaat"))
				.andExpect(jsonPath("$[0].address").value("Akadeemia tee 35"));
	}

	@Test
	void unknownCarrierIsABadRequest() throws Exception {
		mvc.perform(get("/api/shipping/pickup-points").param("carrier", "pigeon").param("country", "EE"))
				.andExpect(status().isBadRequest());
	}
}
