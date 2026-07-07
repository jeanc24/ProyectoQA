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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private StockService stockService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Laptop");
        product.setSku("LAP-001");
        product.setQuantity(10);
        product.setMinStock(2);
    }

    @Test
    void registerInMovement_increasesStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement movement = inv.getArgument(0);
            movement.setId(100L);
            return movement;
        });

        StockMovementResponse response = stockService.registerMovement(
                new StockMovementRequest(1L, MovementType.IN, 5, "Restock"),
                "admin");

        assertThat(response.quantityBefore()).isEqualTo(10);
        assertThat(response.quantityAfter()).isEqualTo(15);
        assertThat(response.quantityDelta()).isEqualTo(5);
        assertThat(response.movementType()).isEqualTo(MovementType.IN);
        assertThat(response.performedBy()).isEqualTo("admin");

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getQuantity()).isEqualTo(15);
    }

    @Test
    void registerOutMovement_decreasesStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement movement = inv.getArgument(0);
            movement.setId(101L);
            return movement;
        });

        StockMovementResponse response = stockService.registerMovement(
                new StockMovementRequest(1L, MovementType.OUT, 3, "Sale"),
                "admin");

        assertThat(response.quantityBefore()).isEqualTo(10);
        assertThat(response.quantityAfter()).isEqualTo(7);
        assertThat(response.quantityDelta()).isEqualTo(-3);
    }

    @Test
    void registerOutMovement_insufficientStockThrows() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockService.registerMovement(
                new StockMovementRequest(1L, MovementType.OUT, 20, null),
                "admin"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void registerAdjustment_setsAbsoluteQuantity() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement movement = inv.getArgument(0);
            movement.setId(102L);
            return movement;
        });

        StockMovementResponse response = stockService.registerMovement(
                new StockMovementRequest(1L, MovementType.ADJUSTMENT, 4, "Inventory count"),
                "admin");

        assertThat(response.quantityBefore()).isEqualTo(10);
        assertThat(response.quantityAfter()).isEqualTo(4);
        assertThat(response.quantityDelta()).isEqualTo(-6);
    }

    @Test
    void registerMovement_unknownProductThrows() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.registerMovement(
                new StockMovementRequest(99L, MovementType.IN, 1, null),
                "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByProductId_unknownProductThrows() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> stockService.findByProductId(99L, org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
