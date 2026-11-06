package com.webshop.orders;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<CustomerOrder, String> {

	Optional<CustomerOrder> findByIdempotencyKey(String idempotencyKey);

	/** Locks the order row, so a callback and a status check can't both change it. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from CustomerOrder o where o.id = :id")
	Optional<CustomerOrder> findByIdForUpdate(@Param("id") String id);
}
