package com.webshop.auth.smartid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Verifies a completed Smart-ID authentication exactly as SK's "Response verification" page requires,
 * and only then says who the user is. Any doubt means the login is refused.
 */
@Component
public class AuthenticationResponseValidator {

	static final String SMART_ID_QUALIFIED_POLICY = "1.3.6.1.4.1.10015.17.2";
	static final String ETSI_NCP_PLUS_POLICY = "0.4.0.2042.1.2";
	static final String SMART_ID_AUTH_EKU = "1.3.6.1.4.1.62306.5.7.0";
	static final String CLIENT_AUTH_EKU = "1.3.6.1.5.5.7.3.2";
	static final Set<String> HASH_ALGORITHMS = Set.of("SHA-256", "SHA-384", "SHA-512", "SHA3-256", "SHA3-384",
			"SHA3-512");

	private static final String SERIAL_NUMBER = "SERIALNUMBER";
	private static final String GIVEN_NAME = "GIVENNAME";
	private static final String SURNAME = "SURNAME";

	private final SmartIdSettings settings;
	private final Set<TrustAnchor> roots;
	private final List<X509Certificate> intermediates;
	private final Supplier<Date> clock;

	@Autowired
	public AuthenticationResponseValidator(SmartIdSettings settings) {
		this(settings, Date::new);
	}

	AuthenticationResponseValidator(SmartIdSettings settings, Supplier<Date> clock) {
		this.settings = settings;
		this.clock = clock;
		String dir = "classpath:smartid/" + settings.environment().code();
		this.roots = new HashSet<>();
		for (X509Certificate root : load(dir + "/roots/*.pem")) {
			roots.add(new TrustAnchor(root, null));
		}
		this.intermediates = load(dir + "/intermediates/*.pem");
		if (roots.isEmpty() || intermediates.isEmpty()) {
			throw new IllegalStateException("No Smart-ID CA certificates found in " + dir);
		}
	}

	/** @return the verified customer; throws {@link SmartIdException} INVALID_RESPONSE otherwise */
	public SmartIdCustomer validate(SessionStatus status, PendingLogin pending) {
		// 1. The session is complete, OK and uses the authentication protocol.
		require("COMPLETE".equals(status.state()), "state is not COMPLETE");
		require(status.result() != null && "OK".equals(status.result().endResult()), "endResult is not OK");
		require("ACSP_V2".equals(status.signatureProtocol()), "signatureProtocol is not ACSP_V2");
		SessionStatus.Signature signature = status.signature();
		SessionStatus.Cert cert = status.cert();
		require(signature != null && cert != null && cert.value() != null, "signature or cert missing");
		require(pending.flowType().equals(signature.flowType()), "unexpected flowType");
		require(interactionTypes(pending.interactions()).contains(status.interactionTypeUsed()),
				"interactionTypeUsed was not requested");

		// 2. The certificate is a valid Smart-ID authentication certificate from our trusted SK CAs.
		X509Certificate certificate = parse(cert.value());
		verifyChain(certificate);
		require(certificate.getBasicConstraints() == -1, "end-entity certificate is a CA");
		verifyPolicies(certificate);
		verifyKeyUsage(certificate);
		require("QUALIFIED".equals(cert.certificateLevel()), "certificate level is not QUALIFIED");

		// 3. It belongs to the person we asked.
		Map<String, String> subject = subject(certificate);
		String identity = subject.get(SERIAL_NUMBER);
		require(identity != null && identity.equals(pending.expectedIdentity()), "certificate is for another person");

		// 4. The signature covers our challenge.
		verifySignature(signature, status.interactionTypeUsed(), pending, certificate);

		return toCustomer(identity, subject.getOrDefault(GIVEN_NAME, ""), subject.getOrDefault(SURNAME, ""));
	}

	private void verifyChain(X509Certificate certificate) {
		try {
			X509CertSelector target = new X509CertSelector();
			target.setCertificate(certificate);
			PKIXBuilderParameters params = new PKIXBuilderParameters(roots, target);
			List<X509Certificate> store = new ArrayList<>(intermediates);
			store.add(certificate);
			params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(store)));
			params.setDate(clock.get());
			CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
			if (settings.checkRevocation()) {
				// OCSP from the certificate's AIA extension, for the user and the intermediate certificate.
				PKIXRevocationChecker revocation = (PKIXRevocationChecker) builder.getRevocationChecker();
				params.addCertPathChecker(revocation);
				params.setRevocationEnabled(true);
			} else {
				params.setRevocationEnabled(false);
			}
			builder.build(params);
		} catch (GeneralSecurityException e) {
			throw new SmartIdException(SmartIdException.Reason.INVALID_RESPONSE,
					"certificate chain or revocation check failed: " + e.getMessage(), e);
		}
	}

	private static void verifyPolicies(X509Certificate certificate) {
		byte[] policies = certificate.getExtensionValue("2.5.29.32");
		require(policies != null, "no certificate policies");
		require(contains(policies, derOid(SMART_ID_QUALIFIED_POLICY)) && contains(policies, derOid(ETSI_NCP_PLUS_POLICY)),
				"not a qualified Smart-ID certificate");
	}

	private static void verifyKeyUsage(X509Certificate certificate) {
		boolean[] keyUsage = certificate.getKeyUsage();
		require(keyUsage != null && keyUsage[0], "no digitalSignature key usage");
		List<String> eku;
		try {
			eku = certificate.getExtendedKeyUsage();
		} catch (CertificateException e) {
			throw new SmartIdException(SmartIdException.Reason.INVALID_RESPONSE, "unreadable extended key usage", e);
		}
		require(eku != null, "no extended key usage");
		boolean current = eku.contains(SMART_ID_AUTH_EKU);
		// Certificates issued before April 2025.
		boolean legacy = eku.contains(CLIENT_AUTH_EKU) && keyUsage.length > 3 && keyUsage[2] && keyUsage[3];
		require(current || legacy, "not an authentication certificate");
	}

	private void verifySignature(SessionStatus.Signature signature, String interactionTypeUsed, PendingLogin pending,
			X509Certificate certificate) {
		require("rsassa-pss".equals(signature.signatureAlgorithm()), "unexpected signature algorithm");
		SessionStatus.SignatureAlgorithmParameters params = signature.signatureAlgorithmParameters();
		require(params != null, "signature parameters missing");
		String hash = params.hashAlgorithm();
		require(pending.hashAlgorithm().equals(hash) && HASH_ALGORITHMS.contains(hash), "unexpected hash algorithm");
		require(params.maskGenAlgorithm() != null && "id-mgf1".equals(params.maskGenAlgorithm().algorithm())
				&& params.maskGenAlgorithm().parameters() != null
				&& hash.equals(params.maskGenAlgorithm().parameters().hashAlgorithm()), "unexpected MGF1 parameters");
		require(params.trailerField() == null || "0xbc".equals(params.trailerField()), "unexpected trailer field");
		require(signature.serverRandom() != null && signature.userChallenge() != null && signature.value() != null,
				"signature fields missing");

		try {
			int saltLength = MessageDigest.getInstance(hash).getDigestLength();
			require(params.saltLength() == null || params.saltLength() == saltLength, "unexpected salt length");
			String payload = acspV2Payload(settings.schemeName(), signature.serverRandom(), pending.rpChallenge(),
					signature.userChallenge(), settings.rpName(), pending.interactions(), interactionTypeUsed,
					signature.flowType());
			Signature verifier = Signature.getInstance("RSASSA-PSS");
			verifier.setParameter(new PSSParameterSpec(hash, "MGF1", new MGF1ParameterSpec(hash), saltLength, 1));
			verifier.initVerify(certificate.getPublicKey());
			verifier.update(payload.getBytes(StandardCharsets.UTF_8));
			require(verifier.verify(Base64.getDecoder().decode(signature.value())), "signature is not valid");
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new SmartIdException(SmartIdException.Reason.INVALID_RESPONSE, "signature check failed", e);
		}
	}

	/**
	 * The data the user's Smart-ID key signed (SK "ACSP_V2 digest calculation"). We are not a broker and use
	 * no callback URL (QR and notification flows), so those two fields stay empty.
	 */
	static String acspV2Payload(String schemeName, String serverRandom, String rpChallenge, String userChallenge,
			String rpName, String interactions, String interactionTypeUsed, String flowType)
			throws GeneralSecurityException {
		Base64.Encoder b64 = Base64.getEncoder();
		String interactionsHash = b64.encodeToString(
				MessageDigest.getInstance("SHA-256").digest(interactions.getBytes(StandardCharsets.UTF_8)));
		return String.join("|", schemeName, "ACSP_V2", serverRandom, rpChallenge, userChallenge,
				b64.encodeToString(rpName.getBytes(StandardCharsets.UTF_8)), "", interactionsHash, interactionTypeUsed,
				"", flowType);
	}

	/** "PNOEE-40504040001" -> customer EE / 40504040001. */
	private static SmartIdCustomer toCustomer(String identity, String firstName, String lastName) {
		require(identity.matches("PNO(EE|LV|LT)-.+"), "unsupported identity type");
		return new SmartIdCustomer(identity.substring(3, 5), identity.substring(6), firstName, lastName, false);
	}

	private static Map<String, String> subject(X509Certificate certificate) {
		String name = certificate.getSubjectX500Principal().getName(X500Principal.RFC2253,
				Map.of("2.5.4.5", SERIAL_NUMBER, "2.5.4.42", GIVEN_NAME, "2.5.4.4", SURNAME));
		try {
			Map<String, String> values = new HashMap<>();
			for (Rdn rdn : new LdapName(name).getRdns()) {
				values.put(rdn.getType(), String.valueOf(rdn.getValue()));
			}
			return values;
		} catch (InvalidNameException e) {
			throw new SmartIdException(SmartIdException.Reason.INVALID_RESPONSE, "unreadable certificate subject", e);
		}
	}

	private static Set<String> interactionTypes(String interactionsBase64) {
		String json = new String(Base64.getDecoder().decode(interactionsBase64), StandardCharsets.UTF_8);
		Set<String> types = new HashSet<>();
		var matcher = java.util.regex.Pattern.compile("\"type\":\"([A-Za-z]+)\"").matcher(json);
		while (matcher.find()) {
			types.add(matcher.group(1));
		}
		return types;
	}

	static X509Certificate parse(String base64Der) {
		try {
			return (X509Certificate) CertificateFactory.getInstance("X.509")
					.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(base64Der)));
		} catch (CertificateException | IllegalArgumentException e) {
			throw new SmartIdException(SmartIdException.Reason.INVALID_RESPONSE, "unreadable certificate", e);
		}
	}

	private static List<X509Certificate> load(String pattern) {
		try {
			List<X509Certificate> certificates = new ArrayList<>();
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			for (Resource resource : new PathMatchingResourcePatternResolver().getResources(pattern)) {
				try (InputStream in = resource.getInputStream()) {
					Collection<? extends java.security.cert.Certificate> found = factory.generateCertificates(in);
					found.forEach(c -> certificates.add((X509Certificate) c));
				}
			}
			return certificates;
		} catch (IOException | CertificateException e) {
			throw new IllegalStateException("Cannot load Smart-ID CA certificates " + pattern, e);
		}
	}

	/** DER encoding of an OID (tag 06), to find it inside the certificate policies extension. */
	static byte[] derOid(String oid) {
		String[] parts = oid.split("\\.");
		var body = new java.io.ByteArrayOutputStream();
		body.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
		for (int i = 2; i < parts.length; i++) {
			long value = Long.parseLong(parts[i]);
			byte[] chunk = new byte[10];
			int n = 0;
			chunk[n++] = (byte) (value & 0x7f);
			while ((value >>>= 7) > 0) {
				chunk[n++] = (byte) (0x80 | (value & 0x7f));
			}
			for (int j = n - 1; j >= 0; j--) {
				body.write(chunk[j]);
			}
		}
		byte[] content = body.toByteArray();
		byte[] der = new byte[content.length + 2];
		der[0] = 0x06;
		der[1] = (byte) content.length;
		System.arraycopy(content, 0, der, 2, content.length);
		return der;
	}

	private static boolean contains(byte[] haystack, byte[] needle) {
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) {
					continue outer;
				}
			}
			return true;
		}
		return false;
	}

	private static void require(boolean condition, String problem) {
		if (!condition) {
			throw new SmartIdException(SmartIdException.Reason.INVALID_RESPONSE, problem);
		}
	}
}
