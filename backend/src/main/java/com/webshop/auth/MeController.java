package com.webshop.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MeController {

	/** Same shape as meSchema in frontend/src/features/auth/types.ts. */
	public record Me(String type, String name, String email) {
	}

	@GetMapping("/api/me")
	public Me me(Authentication authentication) {
		if (!isAdmin(authentication) || !(authentication.getPrincipal() instanceof OidcUser user)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
		}
		String name = user.getFullName() != null ? user.getFullName() : user.getEmail();
		return new Me("admin", name, user.getEmail());
	}

	public static boolean isAdmin(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(a -> AdminOidcUserService.ADMIN_ROLE.equals(a.getAuthority()));
	}
}
