package icc354.pucmm.proyectoqa.domain.repository;

import icc354.pucmm.proyectoqa.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    @Query(
            value = """
                    SELECT p.* FROM products p
                    WHERE (CAST(:namePattern AS TEXT) IS NULL OR p.name ILIKE CAST(:namePattern AS TEXT))
                      AND (CAST(:skuPattern AS TEXT) IS NULL OR p.sku ILIKE CAST(:skuPattern AS TEXT))
                      AND (CAST(:categoryId AS BIGINT) IS NULL OR p.category_id = :categoryId)
                      AND (CAST(:active AS BOOLEAN) IS NULL OR p.active = :active)
                    """,
            countQuery = """
                    SELECT count(*) FROM products p
                    WHERE (CAST(:namePattern AS TEXT) IS NULL OR p.name ILIKE CAST(:namePattern AS TEXT))
                      AND (CAST(:skuPattern AS TEXT) IS NULL OR p.sku ILIKE CAST(:skuPattern AS TEXT))
                      AND (CAST(:categoryId AS BIGINT) IS NULL OR p.category_id = :categoryId)
                      AND (CAST(:active AS BOOLEAN) IS NULL OR p.active = :active)
                    """,
            nativeQuery = true)
    Page<Product> findFiltered(
            @Param("namePattern") String namePattern,
            @Param("skuPattern") String skuPattern,
            @Param("categoryId") Long categoryId,
            @Param("active") Boolean active,
            Pageable pageable);
}
