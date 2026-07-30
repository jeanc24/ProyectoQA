/**
 * Permisos de la app = client roles de Keycloak `inventory-api`.
 *
 * Deben coincidir 1:1 con:
 * - keycloak/inventory-realm.json  (roles.client.inventory-api)
 * - @PreAuthorize("hasAuthority('…')") en los controllers
 *
 * Uso: ProtectedRoute, AppHeader, botones (create, audit, …).
 * La UI solo oculta; sin permiso la API igual responde 403.
 */
export const PERMISSIONS = {
  productView: "product:view",
  productManage: "product:manage",
  stockView: "stock:view",
  stockManage: "stock:manage",
  reportView: "report:view",
  auditView: "audit:view",
  userManage: "user:manage",
} as const;

export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];
