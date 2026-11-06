package com.webshop.orders;

import static com.webshop.TestSupport.admin;
import static com.webshop.TestSupport.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.webshop.products.Product;
import com.webshop.products.ProductRepository;
import com.webshop.shipping.omniva.OmnivaClient;
import com.webshop.shipping.omniva.OmnivaTestData;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CheckoutFlowTest {

	/** Delivery to a real Omniva machine from the test list (Akadeemia tee 35, Tallinn). */
	static final String CHECKOUT = """
			{"email":"mari@example.com","fullName":"Mari Maasikas","phone":"+372 5123 4567",
			 "country":"EE","carrierCode":"omniva","pickupPointId":"96003"}
			""";

	@Autowired
	MockMvc mvc;

	@Autowired
	ProductRepository products;

	@Autowired
	OrderRepository orders;

	@MockitoBean
	OmnivaClient omniva;

	final MockHttpSession session = new MockHttpSession();

	@BeforeEach
	void omnivaList() {
		when(omniva.fetchLocations()).thenReturn(OmnivaTestData.sampleLocations());
	}

	@Test
	void cartTotalsAreExactCentsFromTheDatabase() throws Exception {
		Product mug = products.save(product(1999, 10));
		Product pen = products.save(product(10, 10));

		addToCart(mug, 3);
		addToCart(pen, 2)
				.andExpect(jsonPath("$.items[0].lineTotal.amount").value(5997))
				.andExpect(jsonPath("$.items[1].lineTotal.amount").value(20))
				.andExpect(jsonPath("$.subtotal.amount").value(6017))
				.andExpect(jsonPath("$.subtotal.currency").value("EUR"))
				.andExpect(jsonPath("$.itemCount").value(5));
	}

	@Test
	void outOfStockProductCannotBeAddedToCart() throws Exception {
		Product soldOut = products.save(product(1000, 0));
		addToCart(soldOut, 1).andExpect(status().isConflict());
	}

	@Test
	void quantityIsCappedAtStock() throws Exception {
		Product mug = products.save(product(1000, 2));
		addToCart(mug, 5).andExpect(jsonPath("$.items[0].quantity").value(2));
	}

	@Test
	void orderTotalsAreRecalculatedWithShipping() throws Exception {
		Product mug = products.save(product(1250, 10));
		addToCart(mug, 2); // 25.00, below free shipping

		placeOrder("key-1")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("pending_payment"))
				.andExpect(jsonPath("$.total.amount").value(2500 + 499))
				.andExpect(jsonPath("$.total.currency").value("EUR"))
				.andExpect(jsonPath("$.shipping.amount").value(499))
				.andExpect(jsonPath("$.delivery.carrierCode").value("omniva"))
				.andExpect(jsonPath("$.delivery.pickupPointName").value("Tallinna Akadeemia Coop Konsumi pakiautomaat"))
				.andExpect(jsonPath("$.delivery.address").value("Akadeemia tee 35"))
				.andExpect(jsonPath("$.delivery.city").value("Tallinn"));

		assertThat(products.findById(mug.id).orElseThrow().stock).isEqualTo(8);
		mvc.perform(get("/api/cart").session(session)).andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void shippingIsFreeFromFiftyEuros() throws Exception {
		Product lamp = products.save(product(5000, 1));
		addToCart(lamp, 1);
		placeOrder("key-free").andExpect(jsonPath("$.total.amount").value(5000));
	}

	@Test
	void checkoutIsRefusedWhenAnItemSoldOutMeanwhile() throws Exception {
		Product mug = products.save(product(1000, 3));
		addToCart(mug, 2);
		mug.stock = 0; // someone else bought the rest
		products.saveAndFlush(mug);

		mvc.perform(get("/api/cart").session(session)).andExpect(jsonPath("$.items[0].issue").value("out_of_stock"));
		placeOrder("key-2").andExpect(status().isConflict());
		assertThat(orders.count()).isZero();
	}

	@Test
	void checkoutIsRefusedWhenStockIsLowerThanTheCart() throws Exception {
		Product mug = products.save(product(1000, 5));
		addToCart(mug, 4);
		mug.stock = 2;
		products.saveAndFlush(mug);

		placeOrder("key-3").andExpect(status().isConflict());
		assertThat(products.findById(mug.id).orElseThrow().stock).isEqualTo(2);
	}

	@Test
	void sameIdempotencyKeyReturnsTheSameOrderAndTakesStockOnce() throws Exception {
		Product mug = products.save(product(1000, 5));
		addToCart(mug, 1);

		String first = JsonPath.read(placeOrder("same-key").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
		String second = JsonPath.read(placeOrder("same-key").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.id");

		assertThat(second).isEqualTo(first);
		assertThat(orders.count()).isEqualTo(1);
		assertThat(products.findById(mug.id).orElseThrow().stock).isEqualTo(4);
	}

	@Test
	void orderNeedsIdempotencyKeyAndNonEmptyCart() throws Exception {
		mvc.perform(post("/api/orders").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
				.andExpect(status().isBadRequest());
		placeOrder("key-empty").andExpect(status().isBadRequest());
	}

	@Test
	void parcelMachineMustExistForThatCarrierAndCountry() throws Exception {
		Product mug = products.save(product(1000, 5));
		addToCart(mug, 1);

		placeOrderWith("k-unknown", CHECKOUT.replace("\"96003\"", "\"00000\"")).andExpect(status().isBadRequest());
		// A real Estonian machine, but the order says Latvia.
		placeOrderWith("k-country", CHECKOUT.replace("\"EE\"", "\"LV\"")).andExpect(status().isBadRequest());
		placeOrderWith("k-carrier", CHECKOUT.replace("\"omniva\"", "\"pigeon\"")).andExpect(status().isBadRequest());
		placeOrderWith("k-phone", CHECKOUT.replace("+372 5123 4567", "call me")).andExpect(status().isBadRequest());

		assertThat(orders.count()).isZero();
		assertThat(products.findById(mug.id).orElseThrow().stock).isEqualTo(5);
	}

	@Test
	void invalidCheckoutDetailsAreRejected() throws Exception {
		Product mug = products.save(product(1000, 5));
		addToCart(mug, 1);
		mvc.perform(post("/api/orders").session(session).with(csrf()).header("Idempotency-Key", "k")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CHECKOUT.replace("mari@example.com", "not-an-email")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void onlyTheOwnSessionOrAdminCanSeeAnOrder() throws Exception {
		Product mug = products.save(product(1000, 5));
		addToCart(mug, 1);
		String id = JsonPath.read(placeOrder("key-4").andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/api/orders/" + id).session(session)).andExpect(status().isOk());
		mvc.perform(get("/api/orders/" + id).session(new MockHttpSession())).andExpect(status().isNotFound());
		mvc.perform(get("/api/orders/" + id).with(admin())).andExpect(status().isOk());
	}

	@Test
	void cartQuantityMustBeAWholeNumberInRange() throws Exception {
		Product mug = products.save(product(1000, 50));
		addToCart(mug, 1);
		mvc.perform(patch("/api/cart/items/" + mug.id).session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1.5}"))
				.andExpect(status().isBadRequest());
		mvc.perform(patch("/api/cart/items/" + mug.id).session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":11}"))
				.andExpect(status().isBadRequest());
	}

	private ResultActions addToCart(Product product, int quantity) throws Exception {
		return mvc.perform(post("/api/cart/items").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":\"%s\",\"quantity\":%d}".formatted(product.id, quantity)));
	}

	private ResultActions placeOrder(String key) throws Exception {
		return placeOrderWith(key, CHECKOUT);
	}

	private ResultActions placeOrderWith(String key, String body) throws Exception {
		return mvc.perform(post("/api/orders").session(session).with(csrf()).header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
