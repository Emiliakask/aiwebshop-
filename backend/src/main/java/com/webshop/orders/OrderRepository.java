package com.webshop.orders;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<CustomerOrder, String> {

	Optional<CustomerOrder> findByIdempotencyKey(String idempotencyKey);
}
