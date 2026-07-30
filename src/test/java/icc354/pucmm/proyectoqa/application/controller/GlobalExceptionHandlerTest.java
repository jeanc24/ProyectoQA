package icc354.pucmm.proyectoqa.application.controller;

import icc354.pucmm.proyectoqa.controller.GlobalExceptionHandler;
import icc354.pucmm.proyectoqa.domain.exception.DuplicateSkuException;
import icc354.pucmm.proyectoqa.domain.exception.ResourceNotFoundException;
import icc354.pucmm.proyectoqa.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    /**
     * Caso #1: GlobalExceptionHandler - Recurso no encontrado (404)
     * Verifica que ResourceNotFoundException se mapea a ErrorResponse con status 404 y mensaje adecuado.
     */
    @Test
    void handleNotFound_returns404() {
        ErrorResponse response = handler.handleNotFound(
                new ResourceNotFoundException("Product not found: 1"));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.error()).isEqualTo("Not Found");
        assertThat(response.message()).contains("Product not found: 1");
    }

    /**
     * Caso #2: GlobalExceptionHandler - SKU duplicado (409)
     * Verifica que DuplicateSkuException se mapea a ErrorResponse con status 409 Conflict.
     */
    @Test
    void handleDuplicateSku_returns409() {
        ErrorResponse response = handler.handleDuplicateSku(
                new DuplicateSkuException("SKU already exists: LAP-001"));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.error()).isEqualTo("Conflict");
    }

    /**
     * Caso #3: GlobalExceptionHandler - Validación fallida (400)
     * Verifica que MethodArgumentNotValidException se mapea a ErrorResponse con status 400
     * y lista de errores por campo.
     */
    @Test
    void handleValidation_returns400WithFieldErrors() throws Exception {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));

        var ex = new MethodArgumentNotValidException(null, bindingResult);

        ErrorResponse response = handler.handleValidation(ex);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Validation failed");
        assertThat(response.fieldErrors()).hasSize(1);
        assertThat(response.fieldErrors().getFirst().field()).isEqualTo("name");
    }

    /**
     * Caso #4: GlobalExceptionHandler - Violación de integridad (409)
     * Verifica que DataIntegrityViolationException se mapea a ErrorResponse con status 409.
     */
    @Test
    void handleDataIntegrity_returns409() {
        ErrorResponse response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key"));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.message()).isEqualTo("Data integrity violation");
    }
}