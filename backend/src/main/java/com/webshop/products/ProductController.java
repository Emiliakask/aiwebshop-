package com.webshop.products;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	public record ProductList(List<Product> items, long total) {
	}

	private final ProductRepository products;

	public ProductController(ProductRepository products) {
		this.products = products;
	}

	@GetMapping
	public ProductList list(@RequestParam(defaultValue = "") String search,
			@RequestParam(defaultValue = "featured") String sort) {
		String term = search.trim();
		List<Product> items = products.findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(term, term,
				toSort(sort));
		return new ProductList(items, items.size());
	}

	@GetMapping("/{slug}")
	public Product get(@PathVariable String slug) {
		return products.findBySlug(slug).orElseThrow(ProductController::notFound);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Product create(@Valid @RequestBody Product product) {
		product.id = null; // never let the client pick (or overwrite) an id
		requireFreeSlug(product);
		return products.save(product);
	}

	@PutMapping("/{id}")
	public Product update(@PathVariable UUID id, @Valid @RequestBody Product product) {
		if (!products.existsById(id)) {
			throw notFound();
		}
		product.id = id;
		requireFreeSlug(product);
		return products.save(product);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		if (!products.existsById(id)) {
			throw notFound();
		}
		products.deleteById(id);
	}

	private void requireFreeSlug(Product product) {
		products.findBySlug(product.slug)
				.filter(existing -> !existing.id.equals(product.id))
				.ifPresent(existing -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug is already in use");
				});
	}

	private static Sort toSort(String sort) {
		return switch (sort) {
			case "price-asc" -> Sort.by("price.amount");
			case "price-desc" -> Sort.by("price.amount").descending();
			case "name" -> Sort.by("name");
			default -> Sort.unsorted(); // "featured"
		};
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
	}
}
