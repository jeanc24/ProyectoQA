export type InventorySummary = {
  totalProducts: number;
  activeProducts: number;
  inactiveProducts: number;
  lowStockProducts: number;
  totalUnits: number;
  inventoryValue: number | string;
};

export type TopProduct = {
  productId: number;
  productName: string;
  productSku: string;
  unitsOut: number;
};

export type StockMovement = {
  id: number;
  productId: number;
  productName: string;
  productSku: string;
  movementType: "IN" | "OUT" | "ADJUSTMENT";
  quantityBefore: number;
  quantityAfter: number;
  quantityDelta: number;
  notes: string | null;
  performedBy: string | null;
  createdAt: string;
};
