/** Client roles on Keycloak client `inventory-api` (must match realm + @PreAuthorize). */
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
