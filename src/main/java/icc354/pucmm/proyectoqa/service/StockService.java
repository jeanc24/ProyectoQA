package icc354.pucmm.proyectoqa.application.service;

import icc354.pucmm.proyectoqa.application.dto.StockMovementRequest;
import icc354.pucmm.proyectoqa.application.dto.StockMovementResponse;
import icc354.pucmm.proyectoqa.domain.entity.Product;
import icc354.pucmm.proyectoqa.domain.entity.StockMovement;
import icc354.pucmm.proyectoqa.domain.enums.MovementType;
import icc354.pucmm.proyectoqa.domain.exception.InsufficientStockException;
import icc354.pucmm.proyectoqa.domain.exception.ResourceNotFoundException;
import icc354.pucmm.proyectoqa.domain.repository.ProductRepository;
import icc354.pucmm.proyectoqa.domain.repository.StockMovementRepository;
import icc354.pucmm.proyectoqa.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    public StockService(ProductRepository productRepository,
                        StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    public PageResponse<StockMovementResponse> findAll(
            Long productId,
            MovementType movementType,
            java.time.Instant from,
            java.time.Instant to,
            Pageable pageable) {

        Page<StockMovementResponse> page = stockMovementRepository
                .findFiltered(productId, movementType, from, to, pageable)
                .map(this::toResponse);

        return PageResponse.from(page);
    }

    public PageResponse<StockMovementResponse> findByProductId(Long productId, Pageable pageable) {
        verifyProductExists(productId);

        Page<StockMovementResponse> page = stockMovementRepository
                .findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::toResponse);

        return PageResponse.from(page);
    }

    @Transactional
    public StockMovementResponse registerMovement(StockMovementRequest request, String performedBy) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found: " + request.productId()));

        int before = product.getQuantity();
        int after = calculateQuantityAfter(request.movementType(), before, request.quantity());
        int delta = after - before;

        product.setQuantity(after);
        productRepository.save(product);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType(request.movementType());
        movement.setQuantityBefore(before);
        movement.setQuantityAfter(after);
        movement.setQuantityDelta(delta);
        movement.setNotes(request.notes());
        movement.setPerformedBy(performedBy);

        return toResponse(stockMovementRepository.save(movement));
    }

    private int calculateQuantityAfter(MovementType type, int before, int quantity) {
        return switch (type) {
            case IN -> {
                if (quantity < 1) {
                    throw new IllegalArgumentException("IN movement quantity must be at least 1");
                }
                yield before + quantity;
            }
            case OUT -> {
                if (quantity < 1) {
                    throw new IllegalArgumentException("OUT movement quantity must be at least 1");
                }
                if (quantity > before) {
                    throw new InsufficientStockException(
                            "Insufficient stock: requested " + quantity + ", available " + before);
                }
                yield before - quantity;
            }
            case ADJUSTMENT -> {
                if (quantity < 0) {
                    throw new IllegalArgumentException("ADJUSTMENT quantity cannot be negative");
                }
                yield quantity;
            }
        };
    }

    private void verifyProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }

    private StockMovementResponse toResponse(StockMovement movement) {
        Product product = movement.getProduct();

        return new StockMovementResponse(
                movement.getId(),
                product.getId(),
                product.getName(),
                product.getSku(),
                movement.getMovementType(),
                movement.getQuantityBefore(),
                movement.getQuantityAfter(),
                movement.getQuantityDelta(),
                movement.getNotes(),
                movement.getPerformedBy(),
                movement.getCreatedAt()
        );
    }
}
