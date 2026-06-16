package icc354.pucmm.proyectoqa.application.service;

import icc354.pucmm.proyectoqa.application.dto.ProductRevisionResponse;
import icc354.pucmm.proyectoqa.domain.entity.Product;
import icc354.pucmm.proyectoqa.domain.exception.ResourceNotFoundException;
import icc354.pucmm.proyectoqa.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
// Servicio de auditoría
@Service
// Transacción de solo lectura
@Transactional(readOnly = true)

// Constructor del servicio de auditoría
//Funcion para obtener el historial de un producto y
public class AuditService {

    private final ProductRepository productRepository;
    // EntityManager para la persistencia de la entidad
    @PersistenceContext
    private EntityManager entityManager;

    // Constructor del servicio de auditoría
    public AuditService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Funcion para obtener el historial de un producto y devolverlo en una lista de ProductRevisionResponse
    public List<ProductRevisionResponse> getProductHistory(Long productId) {
        if (!productRepository.existsById(productId) && !hasAuditHistory(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }

        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = auditReader.createQuery()
                .forRevisionsOfEntity(Product.class, false, true)
                .add(AuditEntity.id().eq(productId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<ProductRevisionResponse> history = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            history.add(toResponse(productId, row));
        }
        return history;
    }
    // Funcion para verificar si el producto tiene historial de auditoria
    private boolean hasAuditHistory(Long productId) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        List<Number> revisions = auditReader.getRevisions(Product.class, productId);
        return !revisions.isEmpty();
    }

    // Funcion para convertir el historial de auditoria en un objeto ProductRevisionResponse
    private ProductRevisionResponse toResponse(Long productId, Object[] row) {
        Product product = (Product) row[0];
        DefaultRevisionEntity revisionEntity = (DefaultRevisionEntity) row[1];
        RevisionType revisionType = (RevisionType) row[2];

        Long categoryId = product.getCategory() != null
                ? product.getCategory().getId()
                : null;

        return new ProductRevisionResponse(
                productId,
                (long) revisionEntity.getId(),
                revisionType.name(),
                Instant.ofEpochMilli(revisionEntity.getTimestamp()),
                null,
                product.getName(),
                product.getSku(),
                product.getDescription(),
                categoryId,
                product.getPrice(),
                product.getQuantity(),
                product.getMinStock(),
                product.getActive()
        );
    }
}