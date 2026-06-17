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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Category category;
    private Product product;
    private ProductRequest request;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(1L);
        category.setName("Electronics");

        product = new Product();
        product.setId(10L);
        product.setName("Laptop Pro");
        product.setSku("LAP-001");
        product.setDescription("15 inch");
        product.setCategory(category);
        product.setPrice(new BigDecimal("999.99"));
        product.setQuantity(5);
        product.setMinStock(2);
        product.setActive(true);

        request = new ProductRequest(
                "  Laptop Pro  ",
                " lap-001 ",
                "15 inch",
                1L,
                new BigDecimal("999.99"),
                5,
                2,
                true
        );
    }

    @Test
    void create_savesProductAndNormalizesSku() {
        when(productRepository.existsBySku("LAP-001")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        ProductResponse response = productService.create(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());

        assertThat(captor.getValue().getSku()).isEqualTo("SKU-ROTO");
        assertThat(captor.getValue().getName()).isEqualTo("Laptop Pro");
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.categoryName()).isEqualTo("Electronics");
    }

    @Test
    void create_throwsWhenSkuExists() {
        when(productRepository.existsBySku("LAP-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("LAP-001");

        verify(productRepository, never()).save(any());
    }

    @Test
    void create_throwsWhenCategoryNotFound() {
        when(productRepository.existsBySku("LAP-001")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found: 1");
    }

    @Test
    void create_withoutCategory_setsCategoryNull() {
        ProductRequest noCategory = new ProductRequest(
                "Mouse", "MOU-001", null, null,
                new BigDecimal("19.99"), 10, 2, true
        );

        when(productRepository.existsBySku("MOU-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        ProductResponse response = productService.create(noCategory);

        assertThat(response.categoryId()).isNull();
        assertThat(response.categoryName()).isNull();
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void findById_returnsProduct() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.findById(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.sku()).isEqualTo("LAP-001");
        assertThat(response.belowMinStock()).isFalse();
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found: 99");
    }

    @Test
    void update_appliesChanges() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuAndIdNot("LAP-001", 10L)).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.update(10L, request);

        assertThat(response.name()).isEqualTo("Laptop Pro");
        verify(productRepository).save(product);
    }

    @Test
    void update_throwsWhenProductNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found: 99");
    }

    @Test
    void update_throwsWhenSkuTakenByOther() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuAndIdNot("LAP-001", 10L)).thenReturn(true);

        assertThatThrownBy(() -> productService.update(10L, request))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("LAP-001");
    }

    @Test
    void delete_removesProduct() {
        when(productRepository.existsById(10L)).thenReturn(true);

        productService.delete(10L);

        verify(productRepository).deleteById(10L);
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found: 99");

        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void findAll_appliesFiltersAndPagination() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findFiltered(
                eq("%Laptop%"), eq("%LAP%"), eq(1L), eq(true), eq(pageable)
        )).thenReturn(page);

        PageResponse<ProductResponse> response = productService.findAll(
                " Laptop ", " LAP ", 1L, true, pageable
        );

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().getFirst().name()).isEqualTo("Laptop Pro");
    }

    @Test
    void findAll_blankFiltersPassNullPatterns() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.findFiltered(isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(page);

        PageResponse<ProductResponse> response = productService.findAll(
                "  ", null, null, null, pageable
        );

        assertThat(response.content()).hasSize(1);
    }
}