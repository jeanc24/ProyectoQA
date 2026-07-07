package icc354.pucmm.proyectoqa.domain.repository;

import icc354.pucmm.proyectoqa.domain.entity.StockMovement;
import icc354.pucmm.proyectoqa.domain.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    @Query("""
            SELECT m FROM StockMovement m
            WHERE (:productId IS NULL OR m.product.id = :productId)
              AND (:movementType IS NULL OR m.movementType = :movementType)
              AND (:from IS NULL OR m.createdAt >= :from)
              AND (:to IS NULL OR m.createdAt <= :to)
            ORDER BY m.createdAt DESC
            """)
    Page<StockMovement> findFiltered(
            @Param("productId") Long productId,
            @Param("movementType") MovementType movementType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
