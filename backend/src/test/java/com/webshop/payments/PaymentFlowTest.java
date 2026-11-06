package com.webshop.payments;

import static com.webshop.TestSupport.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

import com.jayway.jsonpath.JsonPath;
import com.webshop.common.Money;
import com.webshop.products.Product;
import com.webshop.products.ProductRepository;
import com.webshop.shipping.omniva.OmnivaClient;
import com.webshop.shipping.omniva.OmnivaTestData;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ProductRepository products;

	@Autowired
	FakePaymentProvider provider;

	@MockitoBean
	OmnivaClient omniva;

	/** Same as CheckoutFlowTest: a real Omniva machine from the test list. */
	static final String CHECKOUT = """
			{"email":"mari@example.com","fullName":"Mari Maasikas","phone":"+372 5123 4567",
			 "country":"EE","carrierCode":"omniva","pickupPointId":"96003"}
			""";

	final MockHttpSession session = new MockHttpSession();
	Product camera;

	@BeforeEach
	void setUp() {
		when(omniva.fetchLocations()).thenReturn(OmnivaTestData.sampleLocations());
		camera = products.save(product(8900, 5));
	}

	@Test
	void newOrderGetsThePaymentPageAndBecomesPaidOnlyWhenTheProviderSaysSo() throws Exception {
		String orderId = placeOrder();
		// 2 x 89.00, free delivery from 50.00
		mvc.perform(get("/api/orders/" + orderId).session(session))
				.andExpect(jsonPath("$.status").value("pending_payment"))
				.andExpect(jsonPath("$.paymentUrl").value("https://pay.example.com/" + orderId));

		// Customer comes back, but the provider hasn't confirmed: still pending.
		mvc.perform(get("/api/payments/fake/return").param("payment_reference", "ref-" + orderId))
				.andExpect(status().isSeeOther())
				.andExpect(header().string("Location", "http://localhost:5173/orders/" + orderId));
		assertStatus(orderId, "pending_payment");

		provider.answer(orderId, PaymentStatus.PAID, Money.eur(17800));
		mvc.perform(post("/api/payments/fake/notify").param("payment_reference", "ref-" + orderId))
				.andExpect(status().isOk());
		mvc.perform(get("/api/orders/" + orderId).session(session))
				.andExpect(jsonPath("$.status").value("paid"))
				.andExpect(jsonPath("$.paymentUrl").doesNotExist());
		assertThat(products.findById(camera.id).orElseThrow().stock).isEqualTo(3);
	}

	@Test
	void failedPaymentReleasesTheStockOnce() throws Exception {
		String orderId = placeOrder();
		assertThat(products.findById(camera.id).orElseThrow().stock).isEqualTo(3);

		provider.answer(orderId, PaymentStatus.FAILED, Money.eur(17800));
		notifyTwice(orderId);

		assertStatus(orderId, "payment_failed");
		assertThat(products.findById(camera.id).orElseThrow().stock).isEqualTo(5);
	}

	@Test
	void aDifferentAmountIsNeverAcceptedAsPaid() throws Exception {
		String orderId = placeOrder();

		provider.answer(orderId, PaymentStatus.PAID, Money.eur(1));
		notifyTwice(orderId);

		assertStatus(orderId, "pending_payment");
	}

	@Test
	void providerRefusingTheStartFailsTheOrderAndReleasesStock() throws Exception {
		provider.refuseNextStart = true;
		String orderId = placeOrder();

		assertStatus(orderId, "payment_failed");
		assertThat(products.findById(camera.id).orElseThrow().stock).isEqualTo(5);
	}

	@Test
	void unknownPaymentReferenceChangesNothing() throws Exception {
		mvc.perform(post("/api/payments/fake/notify").param("payment_reference", "forged"))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/payments/fake/return").param("payment_reference", "forged"))
				.andExpect(header().string("Location", "http://localhost:5173"));
	}

	private String placeOrder() throws Exception {
		mvc.perform(post("/api/cart/items").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":\"" + camera.id + "\",\"quantity\":2}")).andExpect(status().isOk());
		String body = mvc.perform(post("/api/orders").session(session).with(csrf())
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private void notifyTwice(String orderId) throws Exception {
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/payments/fake/notify").param("payment_reference", "ref-" + orderId))
					.andExpect(status().isOk());
		}
	}

	private void assertStatus(String orderId, String expected) throws Exception {
		mvc.perform(get("/api/orders/" + orderId).session(session)).andExpect(jsonPath("$.status").value(expected));
	}
}
