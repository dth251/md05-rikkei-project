export type Role = "USER" | "ADMIN";
export type OrderStatus =
  | "PENDING_PAYMENT"
  | "CONFIRMED"
  | "SHIPPING"
  | "COMPLETED"
  | "CANCELLED"
  | "EXPIRED";
export interface User {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  createdAt: string;
}
export interface Session {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}
export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string;
  unitPrice: number;
  category: string;
  brand: string;
  active: boolean;
  parentId: number | null;
  attributes: Record<string, string>;
  createdAt: string;
}
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Address {
  id?: number;
  recipientName: string;
  phone: string;
  street: string;
  ward: string;
  district: string;
  province: string;
  postalCode: string;
  defaultAddress?: boolean;
}
export interface OrderItem {
  productId: number;
  sku: string;
  name: string;
  unitPrice: number;
  quantity: number;
}
export interface Order {
  id: number;
  userId: number;
  status: OrderStatus;
  channel: "WEB" | "POS";
  totalAmount: number;
  createdAt: string;
  paymentMethod: "ONLINE" | "COD";
  items?: OrderItem[];
  shippingAddress?: Address;
  shipment?: {
    carrier: string;
    trackingNumber: string;
    shippedAt: string;
    completedAt?: string;
  };
  codPaidAt?: string;
}
export interface Payment {
  id: number;
  orderId: number;
  amount: number;
  status: "PENDING" | "SUCCESS" | "FAILED";
  reservationExpired: boolean;
  refundStatus: "NONE" | "REQUIRED" | "REFUNDED";
  paymentMethod: "ONLINE" | "COD";
  completedAt?: string;
  codReceipt?: { receiptNumber: string; collectedAt: string };
}
export interface ReturnCase {
  orderId: number;
  status:
    | "REQUESTED"
    | "APPROVED"
    | "REJECTED"
    | "RECEIVED"
    | "REFUND_PENDING"
    | "REFUNDED";
  reason: string;
  amount: number;
  decisionNote?: string;
  warehouseReceipt?: string;
  restock?: boolean;
  requestedAt: string;
  refundedAt?: string;
}
export interface Stock {
  productId: number;
  available: number;
  reserved: number;
}
export interface CartItem {
  product: Product;
  quantity: number;
}
