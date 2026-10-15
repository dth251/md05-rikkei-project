import { describe, expect, it } from "vitest";
import { actionsFor, paymentMeaning, remainingSeconds } from "./workflow";
import type { Order, Payment, ReturnCase } from "../types";

const createdAt = "2026-10-15T00:00:00Z";
const start = Date.parse(createdAt);
const order = {
  id: 1,
  userId: 1,
  channel: "WEB",
  createdAt,
  status: "PENDING_PAYMENT",
  paymentMethod: "ONLINE",
  totalAmount: 125000,
} as Order;
const pending: Payment = {
  id: 1,
  orderId: 1,
  amount: 125000,
  status: "PENDING",
  reservationExpired: false,
  refundStatus: "NONE",
  paymentMethod: "ONLINE",
};
describe("reservation and payment workflow", () => {
  it("counts from order creation, remains payable before 200 seconds, closes at the exact boundary", () => {
    expect(remainingSeconds(order, start)).toBe(200);
    expect(actionsFor(order, pending, null, start + 199999).checkout).toBe(
      true,
    );
    expect(actionsFor(order, pending, null, start + 200000).checkout).toBe(
      false,
    );
    expect(remainingSeconds(order, start + 205000)).toBe(0);
  });
  it("never offers payment or confirmation when expired even if a late capture succeeds", () => {
    const late = {
      ...pending,
      status: "SUCCESS" as const,
      reservationExpired: true,
      refundStatus: "REQUIRED" as const,
    };
    const expired = { ...order, status: "EXPIRED" as const };
    expect(actionsFor(expired, late, null, start).checkout).toBe(false);
    expect(actionsFor(expired, late).ship).toBe(false);
    expect(actionsFor(expired, late).refund).toBe(true);
    expect(paymentMeaning(expired, late)).toContain("cần hoàn");
  });
  it("does not allow COD collection before shipping or completion before collection acknowledgement", () => {
    const cod = {
      ...order,
      paymentMethod: "COD" as const,
      status: "CONFIRMED" as const,
    };
    expect(actionsFor(cod, pending).collect).toBe(false);
    expect(actionsFor(cod, pending).cancelCod).toBe(true);
    expect(actionsFor({ ...cod, status: "SHIPPING" }, pending).collect).toBe(
      true,
    );
    expect(
      actionsFor(
        { ...cod, status: "SHIPPING" },
        { ...pending, status: "SUCCESS" },
      ).complete,
    ).toBe(false);
    expect(
      actionsFor({ ...cod, status: "SHIPPING", codPaidAt: createdAt }, pending)
        .complete,
    ).toBe(true);
    expect(actionsFor({ ...cod, status: "SHIPPING" }, pending).cancelCod).toBe(
      false,
    );
  });
  it("separates return approval, receipt and refund; requires a successful captured payment for refund", () => {
    const complete = { ...order, status: "COMPLETED" as const };
    expect(actionsFor(complete, pending).requestReturn).toBe(true);
    const requested = { status: "REQUESTED" } as ReturnCase;
    expect(actionsFor(complete, pending, requested).requestReturn).toBe(false);
    expect(actionsFor(complete, pending, requested).receiveReturn).toBe(false);
    expect(actionsFor(complete, pending, requested).decideReturn).toBe(true);
    expect(
      actionsFor(complete, pending, { ...requested, status: "APPROVED" })
        .receiveReturn,
    ).toBe(true);
    expect(
      actionsFor(complete, { ...pending, refundStatus: "REQUIRED" }).refund,
    ).toBe(false);
    expect(
      actionsFor(complete, {
        ...pending,
        status: "SUCCESS",
        refundStatus: "REFUNDED",
      }).refund,
    ).toBe(false);
  });
});
