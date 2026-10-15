import type { Order, Payment, ReturnCase } from "../types";

export const RESERVATION_SECONDS = 200;
export function remainingSeconds(order: Order, now = Date.now()) {
  return Math.max(
    0,
    Math.ceil(
      (new Date(order.createdAt).getTime() + RESERVATION_SECONDS * 1000 - now) /
        1000,
    ),
  );
}
export function actionsFor(
  order: Order,
  payment?: Payment | null,
  returned?: ReturnCase | null,
  now = Date.now(),
) {
  return {
    checkout:
      order.paymentMethod === "ONLINE" &&
      order.status === "PENDING_PAYMENT" &&
      payment?.status === "PENDING" &&
      !payment.reservationExpired &&
      remainingSeconds(order, now) > 0,
    ship: order.status === "CONFIRMED",
    collect:
      order.paymentMethod === "COD" &&
      order.status === "SHIPPING" &&
      payment?.status === "PENDING",
    complete:
      order.status === "SHIPPING" &&
      (order.paymentMethod !== "COD" || Boolean(order.codPaidAt)),
    cancelCod: order.paymentMethod === "COD" && order.status === "CONFIRMED",
    requestReturn: order.status === "COMPLETED" && !returned,
    decideReturn: returned?.status === "REQUESTED",
    receiveReturn: returned?.status === "APPROVED",
    refund:
      payment?.status === "SUCCESS" && payment.refundStatus === "REQUIRED",
  };
}
export function paymentMeaning(order: Order, payment: Payment) {
  if (payment.refundStatus === "REFUNDED")
    return "Khoản thu đã được hoàn tiền.";
  if (payment.refundStatus === "REQUIRED")
    return "Đã ghi nhận thu tiền. Khoản này đang cần hoàn lại.";
  if (order.status === "EXPIRED")
    return "Đơn đã hết hạn và hàng giữ đã được giải phóng.";
  if (payment.status === "SUCCESS") return "Đã ghi nhận thanh toán.";
  return payment.status === "FAILED"
    ? "Thanh toán không thành công."
    : "Chưa ghi nhận thanh toán.";
}
export const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value || 0);
export const dateTime = (value?: string) =>
  value
    ? new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
export const orderLabels: Record<string, string> = {
  PENDING_PAYMENT: "Chờ thanh toán",
  CONFIRMED: "Đã xác nhận",
  SHIPPING: "Đang giao",
  COMPLETED: "Hoàn tất",
  CANCELLED: "Đã hủy",
  EXPIRED: "Hết hạn",
};
export const returnLabels: Record<string, string> = {
  REQUESTED: "Chờ duyệt trả",
  APPROVED: "Đã duyệt trả",
  REJECTED: "Từ chối trả",
  RECEIVED: "Đã nhận hàng",
  REFUND_PENDING: "Chờ hoàn tiền",
  REFUNDED: "Đã hoàn tiền",
};
