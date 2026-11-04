package com.webshop.products;

import java.util.UUID;

import com.webshop.common.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Same shape as productSchema in frontend/src/features/products/types.ts. */
@Entity
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	public UUID id;

	/** Used in product URLs. Same rule as the admin form: lowercase letters, numbers and dashes. */
	@NotNull
	@Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*")
	@Column(unique = true)
	public String slug;

	@NotBlank
	public String name;

	@NotNull
	@Column(columnDefinition = "text")
	public String description;

	@NotBlank
	public String category;

	@NotNull
	@Valid
	@Embedded
	public Money price;

	@NotNull
	@Column(columnDefinition = "text")
	public String imageUrl;

	@NotNull
	public String imageAlt;

	@Min(0)
	public int stock;
}
