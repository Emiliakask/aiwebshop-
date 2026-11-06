package com.webshop.auth.smartid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Customer login with Smart-ID "choose number" (docs/auth.md). Never logs personal codes. */
@Service
public class SmartIdLoginService {

	private static final Logger log = LoggerFactory.getLogger(SmartIdLoginService.class);

	static final String HASH_ALGORITHM = "SHA3-512";
	static final String NOTIFICATION_FLOW = "Notification";
	static final String CHOOSE_NUMBER_INTERACTION = "confirmationMessageAndVerificationCodeChoice";

	public enum Outcome {
		RUNNING, COMPLETE, USER_REFUSED, TIMEOUT, WRONG_VC, ERROR
	}

	public record PollResult(Outcome outcome, SmartIdCustomer customer) {
	}

	private final SmartIdSettings settings;
	private final SmartIdClient client;
	private final AuthenticationResponseValidator validator;
	private final SmartIdChallenges challenges;
	private final SecurityContextRepository securityContexts = new HttpSessionSecurityContextRepository();

	public SmartIdLoginService(SmartIdSettings settings, SmartIdClient client, AuthenticationResponseValidator validator,
			SmartIdChallenges challenges) {
		this.settings = settings;
		this.client = client;
		this.validator = validator;
		this.challenges = challenges;
	}

	/** Starts the login and returns the 4-digit verification code the page must show. */
	public String startChooseNumber(String country, String personalCode, HttpServletRequest request) {
		String identity = "PNO" + country + "-" + normalize(country, personalCode);
		byte[] challengeBytes = challenges.next();
		String rpChallenge = Base64.getEncoder().encodeToString(challengeBytes);
		String interactions = interactions(settings.displayText());

		String sessionId = client.startNotificationAuthentication(identity, rpChallenge, HASH_ALGORITHM, interactions);
		request.getSession().setAttribute(PendingLogin.SESSION_ATTRIBUTE,
				new PendingLogin(sessionId, rpChallenge, interactions, HASH_ALGORITHM, NOTIFICATION_FLOW, identity));
		return verificationCode(challengeBytes);
	}

	/** Asks SK once. On a verified OK the customer is logged in to this browser session. */
	public PollResult poll(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		PendingLogin pending = session == null ? null
				: (PendingLogin) session.getAttribute(PendingLogin.SESSION_ATTRIBUTE);
		if (pending == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No Smart-ID login in progress");
		}

		SessionStatus status;
		try {
			status = client.sessionStatus(pending.sessionId());
		} catch (SmartIdException e) {
			log.warn("Smart-ID status check failed for session {}: {}", pending.sessionId(), e.getMessage());
			session.removeAttribute(PendingLogin.SESSION_ATTRIBUTE);
			return new PollResult(Outcome.ERROR, null);
		}
		if (status.running()) {
			return new PollResult(Outcome.RUNNING, null);
		}
		session.removeAttribute(PendingLogin.SESSION_ATTRIBUTE);

		String endResult = status.result() == null ? null : status.result().endResult();
		if (!"OK".equals(endResult)) {
			log.info("Smart-ID login {} ended with {}", pending.sessionId(), endResult);
			return new PollResult(failure(endResult), null);
		}
		try {
			SmartIdCustomer customer = validator.validate(status, pending);
			logIn(customer, request, response);
			log.info("Smart-ID login {} verified", pending.sessionId());
			return new PollResult(Outcome.COMPLETE, customer);
		} catch (SmartIdException e) {
			log.warn("Smart-ID login {} rejected: {}", pending.sessionId(), e.getMessage());
			return new PollResult(Outcome.ERROR, null);
		}
	}

	/** Demo only (docs/auth.md). */
	public SmartIdCustomer logInTestAccount(HttpServletRequest request, HttpServletResponse response) {
		if (!settings.testAccountEnabled()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		SmartIdCustomer customer = new SmartIdCustomer("EE", "00000000000", "Test", "User", true);
		logIn(customer, request, response);
		return customer;
	}

	/** New session id against session fixation; the session's other contents (cart) stay. */
	private void logIn(SmartIdCustomer customer, HttpServletRequest request, HttpServletResponse response) {
		request.getSession();
		request.changeSessionId();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(customer, null,
				List.of(new SimpleGrantedAuthority(SmartIdCustomer.ROLE))));
		SecurityContextHolder.setContext(context);
		securityContexts.saveContext(context, request, response);
	}

	private static Outcome failure(String endResult) {
		if (endResult == null) {
			return Outcome.ERROR;
		}
		return switch (endResult) {
			case "USER_REFUSED", "USER_REFUSED_INTERACTION", "USER_REFUSED_CERT_CHOICE" -> Outcome.USER_REFUSED;
			case "TIMEOUT" -> Outcome.TIMEOUT;
			case "WRONG_VC" -> Outcome.WRONG_VC;
			default -> Outcome.ERROR;
		};
	}

	/** EE/LT: 11 digits. LV: 123456-12345 (the dash may be left out). */
	static String normalize(String country, String personalCode) {
		String code = personalCode == null ? "" : personalCode.trim();
		boolean valid = switch (country.toUpperCase(Locale.ROOT)) {
			case "EE", "LT" -> code.matches("[1-6][0-9]{10}");
			case "LV" -> {
				if (code.matches("[0-9]{11}")) {
					code = code.substring(0, 6) + "-" + code.substring(6);
				}
				yield code.matches("[0-9]{6}-[0-9]{5}");
			}
			default -> false;
		};
		if (!valid) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid personal code");
		}
		return code;
	}

	/** Base64 of minified JSON; stored as-is in the pending login, because the signature covers these bytes. */
	static String interactions(String displayText) {
		String escaped = displayText.replace("\\", "\\\\").replace("\"", "\\\"");
		String json = "[{\"type\":\"" + CHOOSE_NUMBER_INTERACTION + "\",\"displayText200\":\"" + escaped + "\"}]";
		return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	/** SK: SHA-256 of the raw challenge, last 2 bytes as an unsigned big-endian number, mod 10000, 4 digits. */
	static String verificationCode(byte[] rpChallenge) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(rpChallenge);
			int value = ((hash[hash.length - 2] & 0xff) << 8) | (hash[hash.length - 1] & 0xff);
			return String.format(Locale.ROOT, "%04d", value % 10000);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
