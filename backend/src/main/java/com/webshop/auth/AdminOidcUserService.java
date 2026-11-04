package com.webshop.auth;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Google login for the admin module. Only verified emails listed in ADMIN_EMAILS may log in; everyone
 * else is rejected before a session is created.
 */
@Component
public class AdminOidcUserService extends OidcUserService {

	public static final String ADMIN_ROLE = "ROLE_ADMIN";

	private final Set<String> allowedEmails;

	public AdminOidcUserService(@Value("${app.admin.emails}") List<String> allowedEmails) {
		this.allowedEmails = allowedEmails.stream()
				.map(email -> email.trim().toLowerCase(Locale.ROOT))
				.filter(email -> !email.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) {
		return toAdmin(super.loadUser(userRequest));
	}

	/** Turns a Google user into an admin, or throws if they are not on the allow-list. */
	OidcUser toAdmin(OidcUser user) {
		String email = user.getEmail();
		boolean allowed = email != null
				&& Boolean.TRUE.equals(user.getEmailVerified())
				&& allowedEmails.contains(email.toLowerCase(Locale.ROOT));
		if (!allowed) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error("access_denied", "This Google account is not an admin", null));
		}
		return new DefaultOidcUser(List.of(new SimpleGrantedAuthority(ADMIN_ROLE)), user.getIdToken(),
				user.getUserInfo(), "email");
	}
}
