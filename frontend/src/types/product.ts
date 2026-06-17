export type ProductRequest = {
    name: string;
    sku: string;
    description?: string;
    categoryId?: number | null;
    price: number;
    quantity: number;
    minStock: number;
    active: boolean;
  };
  
  export type ProductResponse = {
    id: number;
    name: string;
    sku: string;
    description: string | null;
    categoryId: number | null;
    categoryName: string | null;
    price: number;
    quantity: number;
    minStock: number;
    active: boolean;
    belowMinStock: boolean;
    createdAt: string;
    updatedAt: string;
  };
  
  export type PageResponse<T> = {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
  };
  
  export type ErrorResponse = {
    status: number;
    error: string;
    message: string;
    fieldErrors?: { field: string; message: string }[];
  };