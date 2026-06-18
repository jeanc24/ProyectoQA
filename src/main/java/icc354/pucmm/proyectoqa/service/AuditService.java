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
// audit service
@Service
// Transaction of only read
@Transactional(readOnly = true)

// Constructor of the audit service
//Function to get the history of a product and return it in a list of ProductRevisionResponse
public class AuditService {

    private final ProductRepository productRepository;
    // EntityManager for the persistence of the entity
    @PersistenceContext
    private EntityManager entityManager;

    // Constructor of the audit service
    public AuditService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Function to get the history of a product and return it in a list of ProductRevisionResponse
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
    // Function to check if the product has audit history
    private boolean hasAuditHistory(Long productId) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        List<Number> revisions = auditReader.getRevisions(Product.class, productId);
        return !revisions.isEmpty();
    }

    // Function to convert the audit history into a ProductRevisionResponse object
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