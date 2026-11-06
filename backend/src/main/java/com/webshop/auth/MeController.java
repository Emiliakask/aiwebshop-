package com.webshop.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.webshop.auth.smartid.SmartIdCustomer;

@RestController
public class MeController {

	/** Same shape as meSchema in frontend/src/features/auth/types.ts. Customers get a masked personal code. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Me(String type, String name, String email, String country, String personalCode) {

		public static Me customer(SmartIdCustomer customer) {
			return new Me("customer", customer.fullName(), null, customer.country(), customer.maskedPersonalCode());
		}
	}

	@GetMapping("/api/me")
	public Me me(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof SmartIdCustomer customer
				&& hasRole(authentication, SmartIdCustomer.ROLE)) {
			return Me.customer(customer);
		}
		if (!isAdmin(authentication) || !(authentication.getPrincipal() instanceof OidcUser user)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
		}
		String name = user.getFullName() != null ? user.getFullName() : user.getEmail();
		return new Me("admin", name, user.getEmail(), null, null);
	}

	public static boolean isAdmin(Authentication authentication) {
		return hasRole(authentication, AdminOidcUserService.ADMIN_ROLE);
	}

	private static boolean hasRole(Authentication authentication, String role) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(a -> role.equals(a.getAuthority()));
	}
}
