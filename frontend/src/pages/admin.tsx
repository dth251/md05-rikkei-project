import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  ArrowUpRight,
  Boxes,
  ClipboardList,
  DollarSign,
  Package,
  Plus,
  Search,
  Pencil,
  Trash2,
  Layers,
  Bell,
  CheckCircle2,
  Mail,
  Store,
  RefreshCw,
} from "lucide-react";
import { api, ApiError, query, readableError } from "../lib/api";
import { money, dateTime } from "../lib/workflow";
import { useCart, useToast } from "../context";
import {
  Badge,
  Empty,
  ErrorBox,
  Loading,
  Modal,
  Pagination,
  ProductArt,
  Quantity,
  useDebounced,
} from "../components";
import { CheckoutPage } from "./shop";
import type { Order, Page, Product, Stock, User, Payment } from "../types";

export function Dashboard() {
  const [orders, setOrders] = useState<Page<Order> | null>(null);
  const [counts, setCounts] = useState<number[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([
      api<Page<Order>>("/orders?size=6"),
      api<Page<Order>>("/orders?size=1&status=PENDING_PAYMENT"),
      api<Page<Order>>("/orders?size=1&status=CONFIRMED"),
      api<Page<Order>>("/orders?size=1&status=COMPLETED"),
      api<Page<Product>>("/products?size=1"),
    ])
      .then(([recent, ...stats]) => {
        if (active) {
          setOrders(recent);
          setCounts(stats.map((s) => s.totalElements));
        }
      })
      .catch((e) => active && setError(readableError(e)));
    return () => {
      active = false;
    };
  }, []);
  const metrics = [
    ["Chờ thanh toán", ClipboardList],
    ["Chờ giao hàng", Boxes],
    ["Đơn hoàn tất", CheckCircle2],
    ["Sản phẩm đang bán", Package],
  ] as const;
  return (
    <>
      <div className="section-heading">
        <div>
          <span className="eyebrow">YOUR BUSINESS, AT A GLANCE</span>
          <h1 className="page-title">Tổng quan cửa hàng</h1>
          <p className="muted">
            Một góc nhìn rõ ràng cho ngày làm việc của bạn.
          </p>
        </div>
        <Link className="button" to="/admin/pos">
          <Plus size={17} /> Tạo đơn tại quầy
        </Link>
      </div>
      <ErrorBox message={error} />
      <div className="metric-grid">
        {metrics.map(([label, Icon], i) => (
          <div className="metric" key={label}>
            <div className="row between">
              <span>{label}</span>
              <Icon size={18} strokeWidth={1.5} />
            </div>
            <strong>{counts[i] ?? "—"}</strong>
            <small>Tổng số hiện tại</small>
          </div>
        ))}
      </div>
      <div className="dashboard-banner">
        <div>
          <span className="eyebrow">KEEP IT SIMPLE</span>
          <h2>Mọi thao tác, đúng thời điểm.</h2>
          <p>Giữ hàng → thanh toán → giao hàng → hoàn tất.</p>
        </div>
        <Link className="button light" to="/admin/orders">
          Xử lý đơn hàng <ArrowUpRight size={17} />
        </Link>
      </div>
      <div className="panel">
        <div className="section-heading">
          <h2>Đơn hàng gần đây</h2>
          <Link className="text-link" to="/admin/orders">
            Xem tất cả <ArrowRight size={16} />
          </Link>
        </div>
        {!orders && !error ? (
          <Loading />
        ) : orders?.content.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Đơn hàng</th>
                  <th>Ngày tạo</th>
                  <th>Kênh</th>
                  <th>Trạng thái</th>
                  <th className="align-right">Giá trị</th>
                </tr>
              </thead>
              <tbody>
                {orders.content.map((o) => (
                  <tr key={o.id}>
                    <td>
                      <Link className="strong" to={`/admin/orders/${o.id}`}>
                        #{o.id}
                      </Link>
                    </td>
                    <td>{dateTime(o.createdAt)}</td>
                    <td>{o.channel}</td>
                    <td>
                      <Badge value={o.status} />
                    </td>
                    <td className="align-right">{money(o.totalAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty title="Chưa có đơn hàng" />
        )}
      </div>
    </>
  );
}
interface ProductDraft {
  sku: string;
  name: string;
  description: string;
  unitPrice: number;
  category: string;
  brand: string;
}
const newProduct = (): ProductDraft => ({
  sku: `MONO-${crypto.randomUUID().slice(0, 8)}`,
  name: "",
  description: "",
  unitPrice: 125000,
  category: "",
  brand: "",
});
function ProductFields({
  value,
  onChange,
}: {
  value: ProductDraft;
  onChange: (draft: ProductDraft) => void;
}) {
  return (
    <div className="form-grid">
      <label>
        Tên sản phẩm
        <input
          required
          maxLength={200}
          value={value.name}
          onChange={(e) => onChange({ ...value, name: e.target.value })}
        />
      </label>
      <label>
        SKU
        <input
          required
          maxLength={64}
          pattern="[A-Za-z0-9][A-Za-z0-9._-]*"
          value={value.sku}
          onChange={(e) => onChange({ ...value, sku: e.target.value })}
        />
      </label>
      <label>
        Giá bán (VND)
        <input
          type="number"
          min="0.01"
          max="999999999999.99"
          step="0.01"
          required
          value={value.unitPrice}
          onChange={(e) =>
            onChange({ ...value, unitPrice: Number(e.target.value) })
          }
        />
      </label>
      <label>
        Danh mục
        <input
          maxLength={100}
          value={value.category}
          onChange={(e) => onChange({ ...value, category: e.target.value })}
        />
      </label>
      <label>
        Thương hiệu
        <input
          maxLength={100}
          value={value.brand}
          onChange={(e) => onChange({ ...value, brand: e.target.value })}
        />
      </label>
      <label className="full">
        Mô tả
        <textarea
          rows={3}
          maxLength={2000}
          value={value.description}
          onChange={(e) => onChange({ ...value, description: e.target.value })}
        />
      </label>
    </div>
  );
}
export function ProductsAdmin() {
  const [data, setData] = useState<Page<Product> | null>(null);
  const [q, setQ] = useState("");
  const search = useDebounced(q);
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<Product | "new" | null>(null);
  const [draft, setDraft] = useState<ProductDraft>(newProduct);
  const [variantParent, setVariantParent] = useState<Product | null>(null);
  const [variants, setVariants] = useState<Product[]>([]);
  const [attributes, setAttributes] = useState('{"color":"Black","size":"M"}');
  const [busy, setBusy] = useState(false);
  const notify = useToast();
  useEffect(() => {
    let active = true;
    setData(null);
    setError("");
    api<Page<Product>>(
      `/products?${query({ page, size: 12, q: search, sort: "newest" })}`,
    )
      .then((d) => active && setData(d))
      .catch((e) => active && setError(readableError(e)));
    return () => {
      active = false;
    };
  }, [search, page, version]);
  async function showVariants(parent: Product) {
    setError("");
    setVariantParent(parent);
    setDraft(newProduct());
    try {
      setVariants(
        await api<Product[]>(
          `/products/${parent.parentId || parent.id}/variants`,
        ),
      );
    } catch (e) {
      setError(readableError(e));
    }
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (variantParent && !editing) {
        const parsed = JSON.parse(attributes);
        if (
          !parsed ||
          Array.isArray(parsed) ||
          Object.values(parsed).some((v) => typeof v !== "string")
        )
          throw new Error(
            "Thuộc tính phải là JSON gồm các cặp tên và giá trị chuỗi.",
          );
        await api(
          `/products/${variantParent.parentId || variantParent.id}/variants`,
          { method: "POST", body: { product: draft, attributes: parsed } },
        );
        await showVariants(variantParent);
      } else {
        await api(
          `/products${editing && editing !== "new" ? `/${editing.id}` : ""}`,
          { method: editing === "new" ? "POST" : "PUT", body: draft },
        );
        setEditing(null);
      }
      setVersion((v) => v + 1);
      notify("Đã lưu sản phẩm.");
    } catch (e) {
      setError(readableError(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="section-heading">
        <div>
          <span className="eyebrow">CATALOG</span>
          <h1 className="page-title">Sản phẩm</h1>
          <p className="muted">Quản lý sản phẩm, SKU và các phiên bản.</p>
        </div>
        <button
          className="button"
          onClick={() => {
            setError("");
            setVariantParent(null);
            setDraft(newProduct());
            setEditing("new");
          }}
        >
          <Plus size={17} /> Thêm sản phẩm
        </button>
      </div>
      <div className="catalog-toolbar">
        <div className="search-input">
          <Search size={17} />
          <input
            aria-label="Tìm sản phẩm quản trị"
            placeholder="Tìm sản phẩm…"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <span className="muted small">
          {data?.totalElements ?? "—"} sản phẩm đang bán
        </span>
      </div>
      <ErrorBox message={error} />
      {(q !== search || !data) && !error ? (
        <Loading />
      ) : data?.content.length ? (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Sản phẩm</th>
                  <th>SKU</th>
                  <th>Danh mục</th>
                  <th className="align-right">Giá</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <div className="row">
                        <div className="table-art">
                          <Package size={22} strokeWidth={1} />
                        </div>
                        <div>
                          <strong>{p.name}</strong>
                          <small>
                            #{p.id}
                            {p.parentId && ` · Biến thể của #${p.parentId}`}
                          </small>
                        </div>
                      </div>
                    </td>
                    <td>{p.sku}</td>
                    <td>{p.category || "—"}</td>
                    <td className="align-right">{money(p.unitPrice)}</td>
                    <td>
                      <div className="row">
                        <button
                          className="icon-button"
                          title="Sửa sản phẩm"
                          aria-label={`Sửa sản phẩm ${p.id}`}
                          onClick={() => {
                            setVariantParent(null);
                            setEditing(p);
                            setDraft({
                              sku: p.sku,
                              name: p.name,
                              description: p.description || "",
                              unitPrice: p.unitPrice,
                              category: p.category || "",
                              brand: p.brand || "",
                            });
                            setError("");
                          }}
                        >
                          <Pencil size={16} />
                        </button>
                        <button
                          className="icon-button"
                          title="Biến thể"
                          aria-label={`Biến thể sản phẩm ${p.id}`}
                          onClick={() => {
                            setEditing(null);
                            showVariants(p);
                          }}
                        >
                          <Layers size={16} />
                        </button>
                        <button
                          className="icon-button"
                          aria-label={`Ngừng bán sản phẩm ${p.id}`}
                          onClick={async () => {
                            if (
                              !confirm(
                                `Ngừng bán ${p.name}? Sản phẩm cha sẽ ẩn cả biến thể.`,
                              )
                            )
                              return;
                            try {
                              await api(`/products/${p.id}`, {
                                method: "DELETE",
                              });
                              setVersion((v) => v + 1);
                              notify("Đã ngừng bán sản phẩm.");
                            } catch (e) {
                              setError(readableError(e));
                            }
                          }}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} pages={data.totalPages} onChange={setPage} />
        </>
      ) : (
        !error && <Empty title="Chưa tìm thấy sản phẩm" />
      )}
      {editing && (
        <Modal
          title={editing === "new" ? "Sản phẩm mới" : "Cập nhật sản phẩm"}
          onClose={() => setEditing(null)}
        >
          <form onSubmit={save}>
            <ErrorBox message={error} />
            <ProductFields value={draft} onChange={setDraft} />
            <button className="button full" disabled={busy}>
              Lưu sản phẩm
            </button>
          </form>
        </Modal>
      )}
      {variantParent && !editing && (
        <Modal
          title={`Biến thể — ${variantParent.name}`}
          onClose={() => setVariantParent(null)}
        >
          <ErrorBox message={error} />
          <div className="variant-list">
            {variants.map((v) => (
              <div className="row between" key={v.id}>
                <div>
                  <strong>{v.name}</strong>
                  <small>
                    {v.sku} ·{" "}
                    {Object.entries(v.attributes || {})
                      .map(([k, x]) => `${k}: ${x}`)
                      .join(", ")}
                  </small>
                </div>
                <span>{money(v.unitPrice)}</span>
              </div>
            ))}
            {!variants.length && <p className="muted">Chưa có biến thể.</p>}
          </div>
          <h3>Thêm biến thể</h3>
          <form onSubmit={save}>
            <ProductFields value={draft} onChange={setDraft} />
            <label>
              Thuộc tính (JSON, bất biến sau khi tạo)
              <textarea
                required
                rows={2}
                value={attributes}
                onChange={(e) => setAttributes(e.target.value)}
              />
            </label>
            <button className="button full" disabled={busy}>
              Tạo biến thể
            </button>
          </form>
        </Modal>
      )}
    </>
  );
}
export function InventoryAdmin() {
  const [data, setData] = useState<Page<Product> | null>(null);
  const [stocks, setStocks] = useState<Record<number, Stock | null>>({});
  const [error, setError] = useState("");
  const [q, setQ] = useState("");
  const search = useDebounced(q);
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  const [receiving, setReceiving] = useState<Product | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef<{ fingerprint: string; key: string } | null>(null);
  const notify = useToast();
  useEffect(() => {
    let active = true;
    setError("");
    setData(null);
    api<Page<Product>>(`/products?${query({ q: search, page, size: 10 })}`)
      .then(async (data) => {
        if (!active) return;
        setData(data);
        const pairs = await Promise.all(
          data.content.map(async (p) => {
            try {
              return [p.id, await api<Stock>(`/inventory/${p.id}`)] as const;
            } catch (e) {
              if (!(e instanceof ApiError && e.status === 404)) throw e;
              return [p.id, null] as const;
            }
          }),
        );
        if (active) setStocks(Object.fromEntries(pairs));
      })
      .catch((e) => active && setError(readableError(e)));
    return () => {
      active = false;
    };
  }, [search, page, version]);
  async function receive(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!receiving) return;
    setBusy(true);
    setError("");
    const quantity = Number(new FormData(e.currentTarget).get("quantity"));
    const fingerprint = `${receiving.id}:${quantity}`;
    if (pending.current?.fingerprint !== fingerprint)
      pending.current = { fingerprint, key: crypto.randomUUID() };
    try {
      await api("/inventory/stocks", {
        method: "POST",
        body: {
          productId: receiving.id,
          quantity,
          idempotencyKey: pending.current.key,
        },
      });
      setReceiving(null);
      setVersion((v) => v + 1);
      notify("Nhập kho thành công.");
    } catch (e) {
      setError(readableError(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="section-heading">
        <div>
          <span className="eyebrow">INVENTORY</span>
          <h1 className="page-title">Tồn kho</h1>
          <p className="muted">
            Kho chung cho WEB và POS. Không trừ lại lượng có thể bán khi chốt
            thanh toán.
          </p>
        </div>
        <button
          className="button secondary small"
          onClick={() => setVersion((v) => v + 1)}
        >
          <RefreshCw size={16} /> Cập nhật
        </button>
      </div>
      <div className="notice">
        <Boxes size={18} />
        <span>
          <strong>Có thể bán</strong> là lượng còn để đặt.{" "}
          <strong>Đang giữ</strong> là lượng dành cho các đơn chưa chốt. ONLINE
          giữ tối đa 200 giây.
        </span>
      </div>
      <div className="catalog-toolbar">
        <div className="search-input">
          <Search size={17} />
          <input
            value={q}
            aria-label="Tìm sản phẩm trong kho"
            placeholder="Tìm sản phẩm trong kho…"
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </div>
      </div>
      <ErrorBox message={error} />
      {(q !== search || !data) && !error ? (
        <Loading />
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Sản phẩm</th>
                  <th>SKU</th>
                  <th className="align-right">Có thể bán</th>
                  <th className="align-right">Đang giữ</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data?.content.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <strong>{p.name}</strong>
                      <small>#{p.id}</small>
                    </td>
                    <td>{p.sku}</td>
                    <td className="align-right">
                      <strong>{stocks[p.id]?.available ?? "—"}</strong>
                    </td>
                    <td className="align-right">
                      {stocks[p.id]?.reserved ?? "—"}
                    </td>
                    <td className="align-right">
                      <button
                        className="button secondary small"
                        onClick={() => {
                          setReceiving(p);
                          pending.current = null;
                          setError("");
                        }}
                      >
                        <Plus size={15} /> Nhập kho
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data && (
            <Pagination
              page={page}
              pages={data.totalPages}
              onChange={setPage}
            />
          )}
        </>
      )}
      {receiving && (
        <Modal
          title={`Nhập kho — ${receiving.name}`}
          onClose={() => setReceiving(null)}
        >
          <form onSubmit={receive}>
            <ErrorBox message={error} />
            <p className="muted">
              SKU {receiving.sku} · #{receiving.id}
            </p>
            <label>
              Số lượng nhập
              <input
                name="quantity"
                type="number"
                min="1"
                max="1000000"
                step="1"
                required
                defaultValue={10}
              />
            </label>
            <button className="button full" disabled={busy}>
              Xác nhận nhập kho
            </button>
          </form>
        </Modal>
      )}
    </>
  );
}
export function CustomersAdmin() {
  const [customer, setCustomer] = useState<User | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function lookup(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setCustomer(null);
    const id = new FormData(e.currentTarget).get("id");
    try {
      setCustomer(await api<User>(`/auth/admin/users/${id}`));
    } catch (e) {
      setError(readableError(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <span className="eyebrow">CUSTOMERS</span>
      <h1 className="page-title">Tra cứu khách hàng</h1>
      <p className="muted">
        Dùng mã khách hàng trên đơn để xem thông tin tài khoản.
      </p>
      <form className="catalog-toolbar" onSubmit={lookup}>
        <div className="search-input">
          <Search size={17} />
          <input
            name="id"
            type="number"
            min="1"
            required
            placeholder="Nhập ID khách hàng"
            aria-label="ID khách hàng"
          />
        </div>
        <button className="button" disabled={busy}>
          Tra cứu
        </button>
      </form>
      <ErrorBox message={error} />
      {customer && (
        <section className="panel customer-result">
          <span className="avatar large-avatar">
            {customer.fullName.charAt(0)}
          </span>
          <div>
            <h2>{customer.fullName}</h2>
            <p>{customer.email}</p>
            <p>
              #{customer.id} · {customer.role}
            </p>
            <small>Đăng ký: {dateTime(customer.createdAt)}</small>
          </div>
        </section>
      )}
    </>
  );
}
export function PaymentsAdmin() {
  const [payment, setPayment] = useState<Payment | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function lookup(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setPayment(null);
    const id = new FormData(e.currentTarget).get("id");
    try {
      setPayment(await api<Payment>(`/payments/${id}`));
    } catch (e) {
      setError(readableError(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <span className="eyebrow">PAYMENTS</span>
      <h1 className="page-title">Tra cứu thanh toán</h1>
      <p className="muted">
        Kiểm tra riêng khoản thu và trạng thái hoàn tiền của giao dịch.
      </p>
      <form className="catalog-toolbar" onSubmit={lookup}>
        <div className="search-input">
          <Search size={17} />
          <input
            name="id"
            type="number"
            min="1"
            required
            placeholder="Nhập mã Payment"
            aria-label="Mã Payment"
          />
        </div>
        <button className="button" disabled={busy}>
          Tra cứu
        </button>
      </form>
      <ErrorBox message={error} />
      {payment && (
        <section className="panel">
          <div className="section-heading">
            <h2>Giao dịch #{payment.id}</h2>
            <Badge value={payment.status} />
          </div>
          <dl className="specs">
            <div>
              <dt>Đơn hàng</dt>
              <dd>
                <Link to={`/admin/orders/${payment.orderId}`}>
                  #{payment.orderId} →
                </Link>
              </dd>
            </div>
            <div>
              <dt>Số tiền</dt>
              <dd>{money(payment.amount)}</dd>
            </div>
            <div>
              <dt>Phương thức</dt>
              <dd>{payment.paymentMethod}</dd>
            </div>
            <div>
              <dt>Hoàn tiền</dt>
              <dd>
                <Badge value={payment.refundStatus} />
              </dd>
            </div>
            <div>
              <dt>Giữ hàng</dt>
              <dd>
                {payment.reservationExpired
                  ? "Đã hết hạn"
                  : "Không bị đánh dấu hết hạn"}
              </dd>
            </div>
          </dl>
          {payment.refundStatus === "REQUIRED" && (
            <p className="notice">
              Đã ghi nhận thu tiền và cần xử lý hoàn. SUCCESS không có nghĩa là
              đơn đã được xác nhận.
            </p>
          )}
          <Link className="button" to={`/admin/orders/${payment.orderId}`}>
            Xử lý tại đơn hàng <ArrowRight size={16} />
          </Link>
        </section>
      )}
    </>
  );
}
export function NotificationsAdmin() {
  return (
    <>
      <span className="eyebrow">COMMUNICATION</span>
      <h1 className="page-title">Thông báo đơn hàng</h1>
      <p className="muted">
        Email HTML được xử lý tự động theo sự kiện nghiệp vụ.
      </p>
      <div className="panel">
        <div className="notification-intro">
          <Mail size={32} strokeWidth={1} />
          <div>
            <h2>Đúng thông tin. Đúng thời điểm.</h2>
            <p>
              Người nhận là email của tài khoản đặt đơn. Trạng thái đơn là căn
              cứ để theo dõi tiến trình.
            </p>
          </div>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Thông báo</th>
                <th>Điều kiện phát sinh</th>
              </tr>
            </thead>
            <tbody>
              {[
                [
                  "Xác nhận đơn ONLINE",
                  "Thanh toán được xác minh và Order chuyển CONFIRMED.",
                ],
                ["Xác nhận COD", "Order nhận acknowledgement đã thu tiền COD."],
                ["Đơn bị hủy", "Đơn chuyển CANCELLED."],
                ["Đơn hết hạn", "ONLINE hết hạn giữ hàng và chuyển EXPIRED."],
                [
                  "Đang giao hàng",
                  "ADMIN ghi nhận giao hàng, trạng thái SHIPPING.",
                ],
                [
                  "Hoàn tất đơn",
                  "ADMIN hoàn tất đúng điều kiện, trạng thái COMPLETED.",
                ],
              ].map(([name, text]) => (
                <tr key={name}>
                  <td>
                    <strong>{name}</strong>
                  </td>
                  <td>{text}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="notice">
          Đây là quy tắc gửi tự động. Giao diện không có dữ liệu xác nhận thư đã
          đến inbox; không dùng trạng thái đơn để khẳng định SMTP đã gửi thành
          công.
        </p>
      </div>
    </>
  );
}
export function PosAdmin() {
  const [data, setData] = useState<Page<Product> | null>(null);
  const [q, setQ] = useState("");
  const search = useDebounced(q);
  const [error, setError] = useState("");
  const [checkout, setCheckout] = useState(false);
  const { items, add, setQuantity, clear } = useCart();
  useEffect(() => {
    let active = true;
    setData(null);
    setError("");
    api<Page<Product>>(`/products?${query({ q: search, size: 24 })}`)
      .then((d) => active && setData(d))
      .catch((e) => active && setError(readableError(e)));
    return () => {
      active = false;
    };
  }, [search]);
  if (checkout)
    return (
      <>
        <button className="text-button" onClick={() => setCheckout(false)}>
          ← Quay lại chọn hàng
        </button>
        <CheckoutPage pos />
      </>
    );
  return (
    <>
      <span className="eyebrow">POINT OF SALE</span>
      <h1 className="page-title">Bán hàng tại quầy</h1>
      <p className="muted">
        Đơn POS dùng cùng giá và tồn kho với cửa hàng trực tuyến.
      </p>
      <div className="pos-grid">
        <section>
          <div className="search-input">
            <Search size={17} />
            <input
              value={q}
              placeholder="Tìm tên hoặc SKU…"
              aria-label="Tìm sản phẩm POS"
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          <ErrorBox message={error} />
          <div className="pos-products">
            {data?.content.map((p) => (
              <button className="pos-product" key={p.id} onClick={() => add(p)}>
                <ProductArt product={p} />
                <strong>{p.name}</strong>
                <span>{money(p.unitPrice)}</span>
                <small>+ Thêm vào đơn</small>
              </button>
            ))}
          </div>
        </section>
        <aside className="summary">
          <div className="row between">
            <h3>Đơn tại quầy</h3>
            <button className="text-button small" onClick={clear}>
              Xóa giỏ
            </button>
          </div>
          {items.map((i) => (
            <div className="pos-line" key={i.product.id}>
              <div>
                <strong>{i.product.name}</strong>
                <small>{money(i.product.unitPrice)}</small>
              </div>
              <Quantity
                value={i.quantity}
                onChange={(n) => setQuantity(i.product.id, n)}
              />
              <button
                className="icon-button"
                aria-label={`Bỏ ${i.product.name}`}
                onClick={() => setQuantity(i.product.id, 0)}
              >
                <Trash2 size={14} />
              </button>
            </div>
          ))}
          {!items.length && <p className="muted">Chọn sản phẩm để bắt đầu.</p>}
          <div className="summary-total">
            <span>Tổng dự kiến</span>
            <strong>
              {money(
                items.reduce(
                  (sum, i) => sum + i.product.unitPrice * i.quantity,
                  0,
                ),
              )}
            </strong>
          </div>
          <button
            className="button full"
            disabled={!items.length}
            onClick={() => setCheckout(true)}
          >
            Tiếp tục <ArrowRight size={16} />
          </button>
        </aside>
      </div>
    </>
  );
}
