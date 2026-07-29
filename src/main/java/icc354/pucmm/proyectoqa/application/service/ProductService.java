package icc354.pucmm.proyectoqa.application.service;

import icc354.pucmm.proyectoqa.application.dto.ProductRequest;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.domain.entity.Category;
import icc354.pucmm.proyectoqa.domain.entity.Product;
import icc354.pucmm.proyectoqa.domain.exception.DuplicateSkuException;
import icc354.pucmm.proyectoqa.domain.exception.ResourceNotFoundException;
import icc354.pucmm.proyectoqa.domain.repository.CategoryRepository;
import icc354.pucmm.proyectoqa.domain.repository.ProductRepository;
import icc354.pucmm.proyectoqa.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public PageResponse<ProductResponse> findAll(
            String name,
            String sku,
            Long categoryId,
            Boolean active,
            Pageable pageable) {

        Page<ProductResponse> page = productRepository.findFiltered(
                toLikePattern(name),
                toLikePattern(sku),
                categoryId,
                active,
                pageable
        ).map(this::toResponse);

        return PageResponse.from(page);
    }

    private static String toLikePattern(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + value.trim() + "%";
    }

    public ProductResponse findById(Long id) {
        Product product = getProductOrThrow(id);
        return toResponse(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.sku());

        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException("SKU already exists: " + sku);
        }

        Product product = new Product();
        applyRequest(product, request, sku);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProductOrThrow(id);
        String sku = normalizeSku(request.sku());

        if (productRepository.existsBySkuAndIdNot(sku, id)) {
            throw new DuplicateSkuException("SKU already exists: " + sku);
        }

        applyRequest(product, request, sku);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        productRepository.deleteById(id);
    }

    private Product getProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found: " + id));
    }

    private void applyRequest(Product product, ProductRequest request, String sku) {
        product.setName(request.name().trim());
        product.setSku(sku);
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setQuantity(request.quantity());
        product.setMinStock(request.minStock());
        product.setActive(request.active());
        product.setCategory(resolveCategory(request.categoryId()));
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category not found: " + categoryId));
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    private ProductResponse toResponse(Product product) {
        Category category = product.getCategory();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                product.getPrice(),
                product.getQuantity(),
                product.getMinStock(),
                product.getActive(),
                product.isBelowMinStock(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
