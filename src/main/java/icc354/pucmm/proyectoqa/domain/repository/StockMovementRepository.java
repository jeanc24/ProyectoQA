package icc354.pucmm.proyectoqa.domain.repository;

import icc354.pucmm.proyectoqa.domain.entity.StockMovement;
import icc354.pucmm.proyectoqa.domain.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public interface StockMovementRepository
        extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    Page<StockMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Top products by units leaving inventory (OUT abs delta).
     * Returns Object[]: [productId, productName, productSku, unitsOut]
     */
    @Query("""
            SELECT m.product.id, m.product.name, m.product.sku, SUM(ABS(m.quantityDelta))
            FROM StockMovement m
            WHERE m.movementType = icc354.pucmm.proyectoqa.domain.enums.MovementType.OUT
            GROUP BY m.product.id, m.product.name, m.product.sku
            ORDER BY SUM(ABS(m.quantityDelta)) DESC
            """)
    List<Object[]> findTopProductsByUnitsOut(Pageable pageable);

    static Specification<StockMovement> withFilters(
            Long productId,
            MovementType movementType,
            Instant from,
            Instant to) {

        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (movementType != null) {
                predicates.add(cb.equal(root.get("movementType"), movementType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            if (query != null) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }

            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
