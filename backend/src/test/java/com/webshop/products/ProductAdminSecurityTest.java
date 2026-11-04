package com.webshop.products;

import static com.webshop.TestSupport.admin;
import static com.webshop.TestSupport.product;
import static com.webshop.TestSupport.productJson;
import static com.webshop.TestSupport.someGoogleUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductAdminSecurityTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ProductRepository products;

	@Test
	void anyoneCanReadProducts() throws Exception {
		Product saved = products.save(product(1299, 3));
		mvc.perform(get("/api/products")).andExpect(status().isOk());
		mvc.perform(get("/api/products/" + saved.slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.price.amount").value(1299))
				.andExpect(jsonPath("$.price.currency").value("EUR"));
	}

	@Test
	void anonymousCannotChangeProducts() throws Exception {
		Product saved = products.save(product(1299, 3));
		mvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(productJson("mug", "1299", "EUR", "5")))
				.andExpect(status().isUnauthorized());
		mvc.perform(put("/api/products/" + saved.id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(productJson("mug", "1299", "EUR", "5")))
				.andExpect(status().isUnauthorized());
		mvc.perform(delete("/api/products/" + saved.id).with(csrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void googleUserWhoIsNotAdminIsForbidden() throws Exception {
		mvc.perform(post("/api/products").with(someGoogleUser()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(productJson("mug", "1299", "EUR", "5")))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanCreateUpdateAndDelete() throws Exception {
		mvc.perform(post("/api/products").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(productJson("mug", "1299", "EUR", "5")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.price.amount").value(1299));

		Product saved = products.findBySlug("mug").orElseThrow();
		mvc.perform(put("/api/products/" + saved.id).with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(productJson("mug", "1500", "EUR", "0")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stock").value(0));

		mvc.perform(delete("/api/products/" + saved.id).with(admin()).with(csrf()))
				.andExpect(status().isNoContent());
	}

	@Test
	void writesWithoutCsrfTokenAreRejected() throws Exception {
		mvc.perform(post("/api/products").with(admin())
				.contentType(MediaType.APPLICATION_JSON).content(productJson("mug", "1299", "EUR", "5")))
				.andExpect(status().isForbidden());
	}

	@Test
	void priceWithAFractionIsRejectedNotTruncated() throws Exception {
		mvc.perform(post("/api/products").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(productJson("mug", "12.99", "EUR", "5")))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/products").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(productJson("mug", "1299.0", "EUR", "5")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void onlyEurIsAccepted() throws Exception {
		mvc.perform(post("/api/products").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(productJson("mug", "1299", "USD", "5")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void negativePriceAndStockAreRejected() throws Exception {
		mvc.perform(post("/api/products").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(productJson("mug", "-1", "EUR", "5")))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/products").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(productJson("mug", "1299", "EUR", "-1")))
				.andExpect(status().isBadRequest());
	}
}
