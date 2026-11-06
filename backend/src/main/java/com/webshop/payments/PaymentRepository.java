package com.webshop.payments;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Optional<Payment> findByProviderCodeAndProviderReference(String providerCode, String providerReference);

	Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(String orderId);
}
