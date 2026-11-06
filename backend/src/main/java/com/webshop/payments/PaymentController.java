package com.webshop.payments;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where providers send the customer back, and where they notify us. Neither trusts its parameters:
 * both only make the backend ask the provider for the real status.
 */
@RestController
public class PaymentController {

	private final PaymentService payments;
	private final String frontendUrl;

	public PaymentController(PaymentService payments, @Value("${app.frontend-url}") String frontendUrl) {
		this.payments = payments;
		this.frontendUrl = frontendUrl.replaceAll("/+$", "");
	}

	/** Customer returns from the payment page. Redirects to the order page, which shows the checked status. */
	@RequestMapping(path = "/api/payments/{provider}/return", method = { RequestMethod.GET, RequestMethod.POST })
	public ResponseEntity<Void> customerReturn(@PathVariable String provider,
			@RequestParam(name = "payment_reference", required = false) String paymentReference) {
		String target = paymentReference == null ? frontendUrl
				: payments.refreshByReference(provider, paymentReference)
						.map(orderId -> frontendUrl + "/orders/" + orderId)
						.orElse(frontendUrl);
		return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(target)).build();
	}

	/** Provider callback (set this URL in the provider's merchant portal). No CSRF token: it's server-to-server. */
	@RequestMapping(path = "/api/payments/{provider}/notify", method = { RequestMethod.GET, RequestMethod.POST })
	public ResponseEntity<Void> notify(@PathVariable String provider,
			@RequestParam(name = "payment_reference") String paymentReference) {
		return payments.refreshByReference(provider, paymentReference).isPresent()
				? ResponseEntity.ok().build()
				: ResponseEntity.notFound().build();
	}
}
