package com.webshop.auth.smartid;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.webshop.auth.MeController.Me;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Customer login. Shapes match frontend/src/features/auth/types.ts. */
@RestController
public class SmartIdController {

	public record Config(String environment, boolean testAccount) {
	}

	public record ChooseNumberRequest(@NotNull @Pattern(regexp = "EE|LV|LT") String country,
			@NotBlank @Size(max = 20) String personalCode) {
	}

	public record ChooseNumberStarted(String verificationCode) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record LoginStatus(String state, String reason, Me me) {
	}

	private final SmartIdSettings settings;
	private final SmartIdLoginService logins;

	public SmartIdController(SmartIdSettings settings, SmartIdLoginService logins) {
		this.settings = settings;
		this.logins = logins;
	}

	@GetMapping("/api/auth/smart-id/config")
	public Config config() {
		return new Config(settings.environment().code(), settings.testAccountEnabled());
	}

	@PostMapping("/api/auth/smart-id/notification")
	public ChooseNumberStarted start(@Valid @RequestBody ChooseNumberRequest body, HttpServletRequest request) {
		return new ChooseNumberStarted(logins.startChooseNumber(body.country(), body.personalCode(), request));
	}

	/** POST, not GET: a completed check logs the browser in, so it is CSRF-protected. */
	@PostMapping("/api/auth/smart-id/status")
	public LoginStatus status(HttpServletRequest request, HttpServletResponse response) {
		SmartIdLoginService.PollResult result = logins.poll(request, response);
		return switch (result.outcome()) {
			case RUNNING -> new LoginStatus("running", null, null);
			case COMPLETE -> new LoginStatus("complete", null, Me.customer(result.customer()));
			default -> new LoginStatus("failed", result.outcome().name().toLowerCase(java.util.Locale.ROOT), null);
		};
	}

	@PostMapping("/api/auth/test-account")
	public Me testAccount(HttpServletRequest request, HttpServletResponse response) {
		return Me.customer(logins.logInTestAccount(request, response));
	}

	/** Same { message } error shape as the rest of the API. */
	@ExceptionHandler(SmartIdException.class)
	ResponseEntity<Map<String, String>> smartIdFailed(SmartIdException e) {
		if (e.reason() == SmartIdException.Reason.NOT_FOUND) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("message", "No Smart-ID account for this personal code"));
		}
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "Smart-ID is not available right now"));
	}
}
