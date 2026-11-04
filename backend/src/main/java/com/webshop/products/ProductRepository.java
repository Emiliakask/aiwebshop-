package com.webshop.products;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	Optional<Product> findBySlug(String slug);

	List<Product> findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(String name, String category, Sort sort);

	/** Locks the rows until the transaction ends, so two checkouts can't sell the same last item. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.id in :ids")
	List<Product> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);
}
