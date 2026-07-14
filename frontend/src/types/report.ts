import type { ProductResponse } from "./product";

export type InventorySummaryResponse = {
  totalProducts: number;
  activeProducts: number;
  inactiveProducts: number;
  lowStockProducts: number;
  totalUnits: number;
  inventoryValue: number;
};

export type MovementType = "IN" | "OUT" | "ADJUSTMENT";

export type StockMovementResponse = {
  id: number;
  productId: number;
  productName: string;
  productSku: string;
  movementType: MovementType;
  quantityBefore: number;
  quantityAfter: number;
  quantityDelta: number;
  notes: string | null;
  performedBy: string;
  createdAt: string;
};

export type TopProductResponse = {
  productId: number;
  productName: string;
  productSku: string;
  unitsOut: number;
};

export type { ProductResponse };
