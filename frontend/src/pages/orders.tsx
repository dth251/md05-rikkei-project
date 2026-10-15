import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  ArrowRight,
  Clock3,
  CreditCard,
  Package,
  RefreshCw,
  Truck,
  Check,
  ExternalLink,
  Search,
} from "lucide-react";
import { api, ApiError, query, readableError } from "../lib/api";
import {
  actionsFor,
  dateTime,
  money,
  orderLabels,
  paymentMeaning,
  remainingSeconds,
} from "../lib/workflow";
import { useToast } from "../context";
import {
  AddressFields,
  Badge,
  emptyAddress,
  Empty,
  ErrorBox,
  Loading,
  Modal,
  Pagination,
} from "../components";
import type { Address, Order, Page, Payment, ReturnCase } from "../types";

export function OrdersPage({ admin = false }: { admin?: boolean }) {
  const [data, setData] = useState<Page<Order> | null>(null);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [channel, setChannel] = useState("");
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  const navigate = useNavigate();
  useEffect(() => {
    let active = true;
    setError("");
    setData(null);
    api<Page<Order>>(`/orders?${query({ page, size: 15, status, channel })}`)
      .then((value) => active && setData(value))
      .catch((e) => active && setError(readableError(e)));
    return () => {
      active = false;
    };
  }, [status, channel, page, version]);
  return (
    <div className={admin ? "" : "container section"}>
      <div className="section-heading">
        <div>
          <span className="eyebrow">
            {admin ? "ORDER MANAGEMENT" : "YOUR JOURNEY"}
          </span>
          <h1 className="page-title">
            {admin ? "Quản lý đơn hàng" : "Đơn hàng của tôi"}
          </h1>
          <p className="muted">
            {admin
              ? "Theo dõi và xử lý đơn theo từng bước."
              : "Tất cả lựa chọn của bạn, ở một nơi."}
          </p>
        </div>
        <button
          className="button secondary small"
          onClick={() => setVersion((v) => v + 1)}
        >
          <RefreshCw size={15} /> Làm mới
        </button>
      </div>
      <div className="catalog-toolbar">
        <select
          aria-label="Lọc trạng thái đơn"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">Mọi trạng thái</option>
          {Object.entries(orderLabels).map(([k, v]) => (
            <option value={k} key={k}>
              {v}
            </option>
          ))}
        </select>
        {admin && (
          <select
            aria-label="Kênh bán hàng"
            value={channel}
            onChange={(e) => {
              setChannel(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Mọi kênh</option>
            <option>WEB</option>
            <option>POS</option>
          </select>
        )}
        <form
          className="search-input"
          onSubmit={(e) => {
            e.preventDefault();
            const id = new FormData(e.currentTarget).get("orderId");
            if (id) navigate(`${admin ? "/admin" : ""}/orders/${id}`);
          }}
        >
          <Search size={17} />
          <input
            name="orderId"
            type="number"
            min="1"
            placeholder="Tra nhanh mã đơn"
            aria-label="Tra mã đơn"
          />
          <button className="text-button">Tra cứu</button>
        </form>
      </div>
      <ErrorBox message={error} />
      {!data && !error ? (
        <Loading />
      ) : data?.content.length ? (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Mã đơn</th>
                  {admin && <th>Khách hàng</th>}
                  <th>Ngày đặt</th>
                  <th>Thanh toán</th>
                  <th>Trạng thái</th>
                  <th className="align-right">Giá trị</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((order) => (
                  <tr key={order.id}>
                    <td>
                      <Link
                        className="strong"
                        to={`${admin ? "/admin" : ""}/orders/${order.id}`}
                      >
                        #{order.id}
                      </Link>
                      <small>{order.channel}</small>
                    </td>
                    {admin && <td>#{order.userId}</td>}
                    <td>{dateTime(order.createdAt)}</td>
                    <td>{order.paymentMethod === "COD" ? "COD" : "ONLINE"}</td>
                    <td>
                      <Badge value={order.status} />
                    </td>
                    <td className="align-right">{money(order.totalAmount)}</td>
                    <td>
                      <Link
                        className="icon-button"
                        aria-label={`Xem đơn ${order.id}`}
                        to={`${admin ? "/admin" : ""}/orders/${order.id}`}
                      >
                        <ArrowRight size={17} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} pages={data.totalPages} onChange={setPage} />
        </>
      ) : (
        !error && (
          <Empty
            title="Chưa có đơn hàng phù hợp"
            action={
              !admin && (
                <Link className="button" to="/">
                  Khám phá cửa hàng
                </Link>
              )
            }
          />
        )
      )}
    </div>
  );
}
type Operation =
  | "ship"
  | "collect"
  | "complete"
  | "cancel"
  | "return"
  | "approve"
  | "reject"
  | "receive"
  | "refund"
  | "reconcile"
  | "simulate-success"
  | "simulate-failed"
  | "simulate-refund";
const labels: Record<Operation, string> = {
  ship: "Giao hàng",
  collect: "Xác nhận đã thu COD",
  complete: "Hoàn tất đơn hàng",
  cancel: "Hủy đơn COD",
  return: "Yêu cầu trả toàn bộ đơn",
  approve: "Duyệt yêu cầu trả",
  reject: "Từ chối yêu cầu trả",
  receive: "Nhận lại hàng",
  refund: "Thực hiện hoàn tiền",
  reconcile: "Đối soát VNPay",
  "simulate-success": "Mô phỏng thanh toán thành công",
  "simulate-failed": "Mô phỏng thanh toán thất bại",
  "simulate-refund": "Mô phỏng hoàn tiền",
};
export function OrderDetail({ admin = false }: { admin?: boolean }) {
  const { id } = useParams();
  const [order, setOrder] = useState<Order | null>(null);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [returned, setReturned] = useState<ReturnCase | null>(null);
  const [error, setError] = useState("");
  const [operation, setOperation] = useState<Operation | null>(null);
  const [busy, setBusy] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [address, setAddress] = useState<Address>(emptyAddress);
  const [sandbox, setSandbox] = useState(false);
  const [refundResult, setRefundResult] = useState<string>("");
  const [actionError, setActionError] = useState("");
  const actionKey = useRef(crypto.randomUUID());
  const notify = useToast();
  const reload = useCallback(async () => {
    const result = await api<Order>(`/orders/${id}`);
    setOrder(result);
    const results = await Promise.allSettled([
      api<Payment>(`/payments/by-order/${id}`),
      api<ReturnCase>(`/orders/${id}/return`),
    ]);
    const p = results[0],
      r = results[1];
    if (p.status === "fulfilled") setPayment(p.value);
    else if (p.reason instanceof ApiError && p.reason.status === 404)
      setPayment(null);
    else throw p.reason;
    if (r.status === "fulfilled") setReturned(r.value);
    else if (r.reason instanceof ApiError && r.reason.status === 404)
      setReturned(null);
    else throw r.reason;
    setError("");
    setSyncing(false);
  }, [id]);
  useEffect(() => {
    setOrder(null);
    setPayment(null);
    setReturned(null);
    reload().catch((e) => setError(readableError(e)));
  }, [reload]);
  const awaiting =
    syncing ||
    (order &&
      (order.status === "PENDING_PAYMENT" ||
        (order.status === "SHIPPING" &&
          order.paymentMethod === "COD" &&
          !order.codPaidAt) ||
        !payment ||
        Boolean(
          returned &&
          ["REQUESTED", "APPROVED", "RECEIVED", "REFUND_PENDING"].includes(
            returned.status,
          ),
        )));
  useEffect(() => {
    if (!awaiting) return;
    let running = false;
    const timer = setInterval(async () => {
      if (running) return;
      running = true;
      try {
        await reload();
      } catch (e) {
        setError(readableError(e));
      } finally {
        running = false;
      }
    }, 3000);
    return () => clearInterval(timer);
  }, [awaiting, reload]);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  function open(next: Operation) {
    setActionError("");
    setOperation(next);
    setAddress(order?.shippingAddress || emptyAddress);
    actionKey.current = crypto.randomUUID();
  }
  async function checkout() {
    if (!payment || !order || !actionsFor(order, payment, returned).checkout)
      return;
    setBusy(true);
    setError("");
    try {
      const result = await api<{ checkoutUrl: string }>(
        `/payments/${payment.id}/vnpay/checkout`,
        { method: "POST" },
      );
      const url = new URL(result.checkoutUrl);
      if (url.protocol !== "https:" || url.hostname !== "sandbox.vnpayment.vn")
        throw new Error("Link thanh toán không thuộc VNPay sandbox.");
      window.location.assign(url.href);
    } catch (e) {
      setError(readableError(e));
      setBusy(false);
    }
  }
  async function perform(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!order || !operation) return;
    setBusy(true);
    setActionError("");
    const form = new FormData(e.currentTarget);
    const paymentPath = `/payments/${payment?.id}`;
    let path = `/orders/${order.id}`,
      body: any;
    switch (operation) {
      case "ship":
        path += "/ship";
        body = {
          carrier: form.get("carrier"),
          trackingNumber: form.get("trackingNumber"),
          shippingAddress: address,
        };
        break;
      case "complete":
        path += "/complete";
        break;
      case "cancel":
        path += "/cod/cancel";
        break;
      case "collect":
        path = `${paymentPath}/cod/collect`;
        body = {
          amount: payment?.amount,
          idempotencyKey: actionKey.current,
          receiptNumber: form.get("receiptNumber"),
        };
        break;
      case "return":
        path += "/return";
        body = {
          reason: form.get("reason"),
          idempotencyKey: actionKey.current,
        };
        break;
      case "approve":
      case "reject":
        path += "/return/decision";
        body = { approved: operation === "approve", note: form.get("note") };
        break;
      case "receive":
        path += "/return/receive";
        body = {
          warehouseReceipt: form.get("warehouseReceipt"),
          restock: form.get("restock") === "on",
        };
        break;
      case "refund":
        path = `${paymentPath}/${order.paymentMethod === "COD" ? "cod" : "vnpay"}/refund`;
        if (order.paymentMethod === "COD")
          body = {
            amount: payment?.amount,
            idempotencyKey: actionKey.current,
            receiptNumber: form.get("receiptNumber"),
          };
        break;
      case "reconcile":
        path = `${paymentPath}/vnpay/reconcile`;
        break;
      case "simulate-success":
      case "simulate-failed":
        path = `${paymentPath}/simulate`;
        body = {
          status: operation === "simulate-success" ? "SUCCESS" : "FAILED",
        };
        break;
      case "simulate-refund":
        path = `${paymentPath}/simulate-refund`;
        break;
    }
    try {
      const result = await api<any>(path, { method: "POST", body });
      if (operation === "refund" || operation === "reconcile")
        setRefundResult(result?.state || result?.status || "Đã gửi yêu cầu");
      setOperation(null);
      setSyncing(true);
      notify("Đã ghi nhận. Các bước liên quan có thể cần vài giây để đồng bộ.");
      try {
        await reload();
      } catch (error) {
        setError(
          "Đã ghi nhận thao tác, đang chờ tải trạng thái mới. " +
            readableError(error),
        );
      }
    } catch (e) {
      setActionError(readableError(e));
    } finally {
      setBusy(false);
    }
  }
  if (!order)
    return (
      <div className={admin ? "" : "container section"}>
        <ErrorBox message={error} />
        {!error && <Loading />}
      </div>
    );
  const actions = actionsFor(order, payment, returned, now);
  const left = remainingSeconds(order, now);
  const stages = ["PENDING_PAYMENT", "CONFIRMED", "SHIPPING", "COMPLETED"];
  const stage = stages.indexOf(order.status);
  return (
    <div className={admin ? "" : "container section"}>
      <Link
        className="text-link back-link"
        to={admin ? "/admin/orders" : "/orders"}
      >
        <ArrowLeft size={15} /> Tất cả đơn hàng
      </Link>
      <div className="section-heading">
        <div>
          <span className="eyebrow">ORDER DETAILS</span>
          <h1 className="page-title">
            Đơn hàng #{order.id} <Badge value={order.status} />
          </h1>
          <p className="muted">
            {dateTime(order.createdAt)} · {order.channel} ·{" "}
            {order.paymentMethod}
          </p>
        </div>
        <button
          className="button secondary small"
          onClick={() => reload().catch((e) => setError(readableError(e)))}
        >
          <RefreshCw size={15} /> Cập nhật
        </button>
      </div>
      <ErrorBox message={error} />
      {order.status === "PENDING_PAYMENT" &&
        order.paymentMethod === "ONLINE" && (
          <div className="reservation-banner">
            <Clock3 size={24} />
            <div>
              <strong>
                {left
                  ? "Sản phẩm đang được giữ cho bạn"
                  : "Đã đến hạn giữ hàng"}
              </strong>
              <p>
                {left
                  ? "Hoàn thành thanh toán trước khi thời gian kết thúc."
                  : "Đang chờ hệ thống cập nhật hết hạn. Không thể tiếp tục thanh toán."}
              </p>
            </div>
            <span className="countdown" aria-label="Thời gian giữ hàng">
              {Math.floor(left / 60)
                .toString()
                .padStart(2, "0")}
              :{(left % 60).toString().padStart(2, "0")}
            </span>
          </div>
        )}
      {stage >= 0 && (
        <div className="order-steps">
          {stages.map((s, i) => (
            <div className={i <= stage ? "done" : ""} key={s}>
              <span>{i < stage ? <Check size={15} /> : i + 1}</span>
              <strong>{orderLabels[s]}</strong>
            </div>
          ))}
        </div>
      )}
      <div className="checkout-grid">
        <div className="stack">
          <section className="panel">
            <h3>Sản phẩm trong đơn</h3>
            {order.items?.map((item) => (
              <div className="order-line" key={item.productId}>
                <div className="mini-art">
                  <Package size={24} strokeWidth={1} />
                </div>
                <div>
                  <strong>{item.name}</strong>
                  <small>
                    {item.sku} · Số lượng {item.quantity}
                  </small>
                </div>
                <span>{money(item.unitPrice * item.quantity)}</span>
              </div>
            ))}
            <div className="summary-total">
              <span>Tổng giá trị</span>
              <strong>{money(order.totalAmount)}</strong>
            </div>
          </section>
          <div className="two-columns">
            <section className="panel">
              <h3>
                <Package size={18} /> Địa chỉ nhận hàng
              </h3>
              {order.shippingAddress ? (
                <>
                  <strong>{order.shippingAddress.recipientName}</strong>
                  <p>{order.shippingAddress.phone}</p>
                  <p className="muted">
                    {[
                      order.shippingAddress.street,
                      order.shippingAddress.ward,
                      order.shippingAddress.district,
                      order.shippingAddress.province,
                    ].join(", ")}
                  </p>
                </>
              ) : (
                <p className="muted">
                  Chưa có địa chỉ. ADMIN cần bổ sung khi giao hàng.
                </p>
              )}
            </section>
            <section className="panel">
              <h3>
                <Truck size={18} /> Giao hàng
              </h3>
              {order.shipment ? (
                <>
                  <strong>{order.shipment.carrier}</strong>
                  <p>Mã vận đơn: {order.shipment.trackingNumber}</p>
                  <small>{dateTime(order.shipment.shippedAt)}</small>
                </>
              ) : (
                <p className="muted">Chưa ghi nhận giao hàng.</p>
              )}
            </section>
          </div>
          {returned && (
            <section className="panel">
              <div className="row between">
                <h3>Hồ sơ trả hàng</h3>
                <Badge value={returned.status} />
              </div>
              <p>{returned.reason}</p>
              <small>{dateTime(returned.requestedAt)}</small>
              {returned.decisionNote && (
                <p>Quyết định: {returned.decisionNote}</p>
              )}
              {returned.warehouseReceipt && (
                <p>
                  Biên nhận kho: {returned.warehouseReceipt} ·{" "}
                  {returned.restock ? "Nhập lại kho" : "Không nhập lại kho"}
                </p>
              )}
              {returned.status === "REFUND_PENDING" && (
                <p className="notice">
                  Đã nhận hàng, đang chờ kết quả hoàn tiền. Đây chưa phải trạng
                  thái đã hoàn.
                </p>
              )}
            </section>
          )}
        </div>
        <aside className="stack">
          <section className="summary">
            <h3>
              <CreditCard size={18} /> Thanh toán
            </h3>
            {payment ? (
              <>
                <div className="row between">
                  <span>Giao dịch #{payment.id}</span>
                  <Badge value={payment.status} />
                </div>
                <p>{paymentMeaning(order, payment)}</p>
                <div className="row between">
                  <span>Số tiền</span>
                  <strong>{money(payment.amount)}</strong>
                </div>
                {payment.refundStatus !== "NONE" && (
                  <div className="row between">
                    <span>Hoàn tiền</span>
                    <Badge value={payment.refundStatus} />
                  </div>
                )}
                {payment.codReceipt && (
                  <p className="small">
                    Biên nhận thu: {payment.codReceipt.receiptNumber}
                  </p>
                )}
                {actions.checkout && (
                  <button
                    className="button full"
                    disabled={busy}
                    onClick={checkout}
                  >
                    Thanh toán VNPay <ExternalLink size={16} />
                  </button>
                )}
              </>
            ) : (
              <p className="muted">
                Đang chờ tạo giao dịch. Trang sẽ tự cập nhật.
              </p>
            )}
          </section>
          {!admin && actions.requestReturn && (
            <button className="button secondary" onClick={() => open("return")}>
              Yêu cầu trả hàng
            </button>
          )}
          {admin && (
            <section className="panel actions-panel">
              <h3>Xử lý đơn</h3>
              {actions.ship && (
                <button className="button full" onClick={() => open("ship")}>
                  Giao hàng
                </button>
              )}
              {actions.collect && (
                <button className="button full" onClick={() => open("collect")}>
                  Ghi nhận thu COD
                </button>
              )}
              {actions.complete && (
                <button
                  className="button full"
                  onClick={() => open("complete")}
                >
                  Hoàn tất đơn
                </button>
              )}
              {order.paymentMethod === "COD" &&
                order.status === "SHIPPING" &&
                !order.codPaidAt && (
                  <p className="small muted">
                    Chỉ hoàn tất sau khi hệ thống ghi nhận đã thu đủ COD.
                  </p>
                )}
              {actions.cancelCod && (
                <button
                  className="button secondary full"
                  onClick={() => open("cancel")}
                >
                  Hủy trước giao
                </button>
              )}
              {actions.requestReturn && (
                <button
                  className="button secondary full"
                  onClick={() => open("return")}
                >
                  Tạo yêu cầu trả hàng
                </button>
              )}
              {actions.decideReturn && (
                <>
                  <button
                    className="button full"
                    onClick={() => open("approve")}
                  >
                    Duyệt trả hàng
                  </button>
                  <button
                    className="button secondary full"
                    onClick={() => open("reject")}
                  >
                    Từ chối trả
                  </button>
                </>
              )}
              {actions.receiveReturn && (
                <button className="button full" onClick={() => open("receive")}>
                  Nhận hàng trả
                </button>
              )}
              {actions.refund && (
                <>
                  <button
                    className="button full"
                    onClick={() => open("refund")}
                  >
                    {order.paymentMethod === "COD"
                      ? "Ghi nhận chi hoàn COD"
                      : "Yêu cầu hoàn VNPay"}
                  </button>
                  {order.paymentMethod === "ONLINE" && (
                    <button
                      className="button secondary full"
                      onClick={() => open("reconcile")}
                    >
                      Đối soát hoàn tiền
                    </button>
                  )}
                </>
              )}
              {refundResult && (
                <p className="notice">
                  Kết quả yêu cầu: {refundResult}. Trạng thái hoàn cuối cùng nằm
                  ở hồ sơ trả và Payment.
                </p>
              )}
              <button
                className="text-button small"
                onClick={() => setSandbox(!sandbox)}
              >
                Công cụ kiểm thử sandbox
              </button>
              {sandbox && (
                <div className="sandbox-tools">
                  <p className="small muted">
                    Dành cho ONLINE mô phỏng, chưa có giao dịch VNPay. Backend
                    từ chối nếu không phù hợp.
                  </p>
                  {payment?.status === "PENDING" &&
                    order.status === "PENDING_PAYMENT" && (
                      <>
                        <button
                          className="button secondary small"
                          onClick={() => open("simulate-success")}
                        >
                          Simulate SUCCESS
                        </button>
                        <button
                          className="button secondary small"
                          onClick={() => open("simulate-failed")}
                        >
                          Simulate FAILED
                        </button>
                      </>
                    )}
                  {actions.refund && order.paymentMethod === "ONLINE" && (
                    <button
                      className="button secondary small"
                      onClick={() => open("simulate-refund")}
                    >
                      Simulate refund
                    </button>
                  )}
                </div>
              )}
            </section>
          )}
          <p className="small muted">
            Thông báo email được hệ thống xử lý tự động theo các mốc xác nhận,
            hủy, hết hạn, giao và hoàn tất.
          </p>
        </aside>
      </div>
      {operation && (
        <Modal
          title={labels[operation]}
          onClose={() => !busy && setOperation(null)}
        >
          <form onSubmit={perform}>
            <ErrorBox message={actionError} />
            {operation === "ship" && (
              <>
                <label>
                  Đơn vị vận chuyển
                  <input name="carrier" required maxLength={100} />
                </label>
                <label>
                  Mã vận đơn
                  <input name="trackingNumber" required maxLength={128} />
                </label>
                <AddressFields value={address} onChange={setAddress} />
              </>
            )}
            {operation === "return" && (
              <label>
                Lý do trả toàn bộ đơn
                <textarea
                  name="reason"
                  required
                  minLength={1}
                  maxLength={500}
                  rows={4}
                />
              </label>
            )}
            {(operation === "approve" || operation === "reject") && (
              <label>
                Ghi chú quyết định
                <textarea name="note" required maxLength={500} rows={3} />
              </label>
            )}
            {operation === "receive" && (
              <>
                <label>
                  Biên nhận nhập kho
                  <input
                    name="warehouseReceipt"
                    required
                    maxLength={128}
                    defaultValue={`RETURN-${order.id}-${Date.now()}`}
                  />
                </label>
                <label className="checkbox">
                  <input type="checkbox" name="restock" defaultChecked /> Hàng
                  có thể bán lại, cộng vào kho
                </label>
              </>
            )}
            {(operation === "collect" ||
              (operation === "refund" && order.paymentMethod === "COD")) && (
              <>
                <p>
                  Số tiền toàn bộ:{" "}
                  <strong>{money(payment?.amount || 0)}</strong>
                </p>
                <label>
                  Số biên nhận {operation === "collect" ? "thu" : "chi hoàn"}
                  <input
                    name="receiptNumber"
                    required
                    maxLength={128}
                    defaultValue={`${operation.toUpperCase()}-${order.id}-${Date.now()}`}
                  />
                </label>
                <p className="notice">
                  Chỉ xác nhận khi bạn đã thực sự{" "}
                  {operation === "collect" ? "nhận" : "chi hoàn"} đủ tiền. Hệ
                  thống lưu biên nhận, không tự chuyển tiền mặt.
                </p>
              </>
            )}
            {[
              "complete",
              "cancel",
              "refund",
              "reconcile",
              "simulate-success",
              "simulate-failed",
              "simulate-refund",
            ].includes(operation) && (
              <p>
                Xác nhận “{labels[operation]}” cho đơn #{order.id}?{" "}
                {operation === "refund" &&
                  order.paymentMethod === "ONLINE" &&
                  "Nếu kết quả chưa rõ, dùng đối soát thay vì gửi lại yêu cầu hoàn."}
              </p>
            )}
            <button className="button full" disabled={busy}>
              {busy ? "Đang xử lý…" : "Xác nhận"}
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}
