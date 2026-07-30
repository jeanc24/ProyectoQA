package icc354.pucmm.proyectoqa.application.integration;



import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;

import org.springframework.http.MediaType;

import org.springframework.http.ResponseEntity;

import org.springframework.web.client.HttpClientErrorException;

import org.testcontainers.junit.jupiter.Testcontainers;



import java.util.Map;



import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;



/**

 * TEST-01 — Integration tests de seguridad con Keycloak (Testcontainers).

 *

 * Escenarios mínimos:

 * <ul>

 *   <li>sin token → 401</li>

 *   <li>viewer con product:view → 200 en GET products</li>

 *   <li>viewer sin product:manage → 403 en POST products</li>

 *   <li>viewer sin report:view → 403 en reports</li>

 *   <li>admin con report:view → 200 en reports</li>

 *   <li>viewer sin audit:view → 403 en audit</li>

 * </ul>

 */

@Testcontainers

class KeycloakSecurityIntegrationTest extends AbstractKeycloakIntegrationTest {



    /**

     * Caso de integración #1: Sin token en productos

     * Verifica que GET /api/v1/products sin autenticación devuelve 401 Unauthorized.

     */

    @Test

    void products_withoutToken_returns401() {

        assertThatThrownBy(() ->

                restClient().get()

                        .uri(apiUrl("/api/v1/products"))

                        .retrieve()

                        .toBodilessEntity())

                .isInstanceOf(HttpClientErrorException.Unauthorized.class)

                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode())

                .isEqualTo(HttpStatus.UNAUTHORIZED);

    }



    /**

     * Caso de integración #2: Viewer lista productos

     * Verifica que un usuario viewer con product:view obtiene 200 al listar productos.

     */

    @Test

    void products_asViewer_returns200() {

        String token = getAccessToken("viewer", "viewer");



        ResponseEntity<Map> response = restClient().get()

                .uri(apiUrl("/api/v1/products?size=5"))

                .header("Authorization", "Bearer " + token)

                .retrieve()

                .toEntity(Map.class);



        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).containsKey("content");

    }



    /**

     * Caso de integración #3: Viewer no puede crear

     * Verifica que un viewer sin product:manage recibe 403 al intentar POST /api/v1/products.

     */

    @Test

    void createProduct_asViewer_returns403() {

        String token = getAccessToken("viewer", "viewer");

        String body = """

                {

                  "name": "Blocked Product",

                  "sku": "VIEWER-BLOCK-001",

                  "description": "should fail",

                  "categoryId": null,

                  "price": 10.00,

                  "quantity": 1,

                  "minStock": 0,

                  "active": true

                }

                """;



        assertThatThrownBy(() ->

                restClient().post()

                        .uri(apiUrl("/api/v1/products"))

                        .header("Authorization", "Bearer " + token)

                        .contentType(MediaType.APPLICATION_JSON)

                        .body(body)

                        .retrieve()

                        .toBodilessEntity())

                .isInstanceOf(HttpClientErrorException.Forbidden.class)

                .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode())

                .isEqualTo(HttpStatus.FORBIDDEN);

    }



    /**

     * Caso de integración #4: Viewer sin reportes

     * Verifica que un viewer sin report:view recibe 403 al consultar inventory-summary.

     */

    @Test

    void reports_asViewer_returns403() {

        String token = getAccessToken("viewer", "viewer");



        assertThatThrownBy(() ->

                restClient().get()

                        .uri(apiUrl("/api/v1/reports/inventory-summary"))

                        .header("Authorization", "Bearer " + token)

                        .retrieve()

                        .toBodilessEntity())

                .isInstanceOf(HttpClientErrorException.Forbidden.class);

    }



    /**

     * Caso de integración #5: Admin accede a reportes

     * Verifica que un admin con report:view obtiene 200 y los campos del resumen de inventario.

     */

    @Test

    void reports_asAdmin_returns200() {

        String token = getAccessToken("admin", "admin");



        ResponseEntity<Map> response = restClient().get()

                .uri(apiUrl("/api/v1/reports/inventory-summary"))

                .header("Authorization", "Bearer " + token)

                .retrieve()

                .toEntity(Map.class);



        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(response.getBody()).containsKeys(

                "totalProducts", "activeProducts", "lowStockProducts");

    }



    /**

     * Caso de integración #6: Viewer sin auditoría

     * Verifica que un viewer sin audit:view recibe 403 al consultar historial de auditoría.

     */

    @Test

    void audit_asViewer_returns403() {

        String token = getAccessToken("viewer", "viewer");



        assertThatThrownBy(() ->

                restClient().get()

                        .uri(apiUrl("/api/v1/audit/products/1"))

                        .header("Authorization", "Bearer " + token)

                        .retrieve()

                        .toBodilessEntity())

                .isInstanceOf(HttpClientErrorException.Forbidden.class);

    }



    /**

     * Caso de integración #7: Admin accede a auditoría

     * Verifica que un admin con audit:view obtiene 200 o 404 si el producto no existe.

     */

    @Test

    void audit_asAdmin_returns200Or404() {

        // admin tiene audit:view; 404 si el producto no existe aún es OK (permiso pasó)

        String token = getAccessToken("admin", "admin");



        try {

            ResponseEntity<Void> response = restClient().get()

                    .uri(apiUrl("/api/v1/audit/products/1"))

                    .header("Authorization", "Bearer " + token)

                    .retrieve()

                    .toBodilessEntity();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        } catch (HttpClientErrorException.NotFound notFound) {

            assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        }

    }

}

