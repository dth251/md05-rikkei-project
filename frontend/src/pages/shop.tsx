import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import {
  ArrowRight,
  ArrowUpRight,
  Check,
  Search,
  SlidersHorizontal,
  ShoppingBag,
  Trash2,
  Plus,
  MapPin,
  ShieldCheck,
  Package,
  ArrowLeft,
  LogOut,
} from "lucide-react";
import { api, readableError, saveSession } from "../lib/api";
import { money, dateTime } from "../lib/workflow";
import { useAuth, useCart, useToast } from "../context";
import {
  AddressFields,
  emptyAddress,
  Empty,
  ErrorBox,
  Loading,
  Modal,
  Pagination,
  ProductArt,
  ProductCard,
  Quantity,
  useDebounced,
} from "../components";
import type { Address, Order, Page, Product, Stock } from "../types";

export function ShopHome() {
  const [data, setData] = useState<Page<Product> | null>(null);
  const [error, setError] = useState("");
  const [q, setQ] = useState("");
  const search = useDebounced(q);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState("newest");
  const [category, setCategory] = useState("");
  const [brand, setBrand] = useState("");
  const [min, setMin] = useState("");
  const [max, setMax] = useState("");
  const [advanced, setAdvanced] = useState(false);
  const filters = useDebounced(
    JSON.stringify({
      q: search,
      page,
      size: 12,
      sort,
      category,
      brand,
      minPrice: min,
      maxPrice: max,
    }),
  );
  useEffect(() => {
    let active = true;
    setError("");
    setData(null);
    const values = JSON.parse(filters);
    const params = new URLSearchParams(
      Object.entries(values)
        .filter(([, value]) => value !== "")
        .map(([key, value]) => [key, String(value)]),
    );
    api<Page<Product>>(`/products?${params}`)
      .then((value) => {
        if (active) setData(value);
      })
      .catch((e) => {
        if (active) setError(readableError(e));
      });
    return () => {
      active = false;
    };
  }, [filters]);
  return (
    <>
      <section className="hero container">
        <div className="hero-copy">
          <div className="eyebrow">
            <span className="line" /> THE EVERYDAY COLLECTION
          </div>
          <h1>
            Ít hơn.
            <br />
            <em>Đúng hơn.</em>
          </h1>
          <p>
            Những lựa chọn thiết yếu cho mỗi ngày.
            <br />
            Tối giản từ phong cách đến trải nghiệm mua sắm.
          </p>
          <a href="#collection" className="button">
            Khám phá sản phẩm <ArrowUpRight size={18} />
          </a>
          <div className="hero-footnote">
            <span>01 —</span> Essentials, considered.
          </div>
        </div>
        <div className="hero-art">
          <span className="hero-art-label">THE ART OF LESS / 2026</span>
          <div className="hero-object">
            <ShoppingBag size={210} strokeWidth={0.65} />
          </div>
          <div className="hero-caption">
            <span>EVERYDAY, REDEFINED.</span>
            <ArrowUpRight size={24} />
          </div>
        </div>
      </section>
      <div className="service-strip">
        <div>
          <Package size={18} /> Theo dõi từng bước giao hàng
        </div>
        <div>
          <ShieldCheck size={18} /> Thanh toán VNPay hoặc COD
        </div>
        <div>
          <ShoppingBag size={18} /> Trả hàng theo quy trình rõ ràng
        </div>
      </div>
      <section id="collection" className="container section">
        <div className="section-heading">
          <div>
            <span className="eyebrow">CHỌN ĐIỀU PHÙ HỢP</span>
            <h2>
              Bộ sưu tập thiết yếu
              <span className="count">{data?.totalElements ?? "—"}</span>
            </h2>
          </div>
          <span className="muted">Dành cho mỗi ngày của bạn.</span>
        </div>
        <form className="catalog-toolbar" onSubmit={(e) => e.preventDefault()}>
          <div className="search-input">
            <Search size={18} />
            <input
              aria-label="Tìm sản phẩm"
              placeholder="Tìm tên, SKU, mô tả…"
              value={q}
              maxLength={100}
              onChange={(e) => {
                setQ(e.target.value);
                setPage(0);
              }}
            />
          </div>
          <button
            type="button"
            className="button secondary"
            onClick={() => setAdvanced(!advanced)}
          >
            <SlidersHorizontal size={16} /> Bộ lọc
          </button>
          <select
            aria-label="Sắp xếp sản phẩm"
            value={sort}
            onChange={(e) => {
              setSort(e.target.value);
              setPage(0);
            }}
          >
            <option value="newest">Mới nhất</option>
            <option value="price-asc">Giá tăng dần</option>
            <option value="price-desc">Giá giảm dần</option>
            <option value="name">Tên A–Z</option>
          </select>
        </form>
        {advanced && (
          <div className="filter-panel">
            <label>
              Danh mục
              <input
                value={category}
                onChange={(e) => {
                  setCategory(e.target.value);
                  setPage(0);
                }}
              />
            </label>
            <label>
              Thương hiệu
              <input
                value={brand}
                onChange={(e) => {
                  setBrand(e.target.value);
                  setPage(0);
                }}
              />
            </label>
            <label>
              Giá từ
              <input
                type="number"
                min="0.01"
                value={min}
                onChange={(e) => {
                  setMin(e.target.value);
                  setPage(0);
                }}
              />
            </label>
            <label>
              Đến
              <input
                type="number"
                min="0.01"
                value={max}
                onChange={(e) => {
                  setMax(e.target.value);
                  setPage(0);
                }}
              />
            </label>
            <button
              className="text-button"
              onClick={() => {
                setCategory("");
                setBrand("");
                setMin("");
                setMax("");
                setQ("");
              }}
            >
              Xóa bộ lọc
            </button>
          </div>
        )}
        <ErrorBox message={error} />
        {!data && !error ? (
          <Loading />
        ) : data?.content.length ? (
          <>
            <div className="product-grid">
              {data.content.map((product) => (
                <ProductCard product={product} key={product.id} />
              ))}
            </div>
            <Pagination
              page={page}
              pages={data.totalPages}
              onChange={setPage}
            />
          </>
        ) : (
          !error && (
            <Empty
              title="Chưa tìm thấy sản phẩm"
              text="Thử một từ khóa hoặc bộ lọc khác."
            />
          )
        )}
      </section>
      <section className="editorial container">
        <span className="eyebrow">THE MONO PHILOSOPHY</span>
        <h2>
          Không cần nhiều hơn.
          <br />
          <em>Chỉ cần phù hợp hơn.</em>
        </h2>
        <p>
          Một không gian mua sắm gọn gàng,
          <br />
          để bạn tập trung vào những điều mình cần.
        </p>
      </section>
    </>
  );
}
export function ProductDetail() {
  const { id } = useParams();
  const [product, setProduct] = useState<Product | null>(null);
  const [variants, setVariants] = useState<Product[]>([]);
  const [stock, setStock] = useState<Stock | null>(null);
  const [error, setError] = useState("");
  const [quantity, setQuantity] = useState(1);
  const { user } = useAuth();
  const cart = useCart();
  const notify = useToast();
  useEffect(() => {
    let active = true;
    setProduct(null);
    setError("");
    setQuantity(1);
    setStock(null);
    api<Product>(`/products/${id}`)
      .then(async (p) => {
        if (!active) return;
        setProduct(p);
        try {
          const v = await api<Product[]>(
            `/products/${p.parentId || p.id}/variants`,
          );
          if (active) setVariants(Array.isArray(v) ? v : []);
        } catch {
          if (active) setVariants([]);
        }
      })
      .catch((e) => active && setError(readableError(e)));
    if (user)
      api<Stock>(`/inventory/${id}`)
        .then((s) => active && setStock(s))
        .catch(() => {});
    return () => {
      active = false;
    };
  }, [id, user?.id]);
  if (error)
    return (
      <div className="container section">
        <ErrorBox message={error} />
      </div>
    );
  if (!product) return <Loading />;
  return (
    <div className="container section">
      <div className="breadcrumbs">
        <Link to="/">Cửa hàng</Link>
        <span>/</span>
        <span>{product.name}</span>
      </div>
      <div className="product-detail">
        <ProductArt product={product} large />
        <div className="product-info">
          <span className="eyebrow">{product.brand || "MONO ESSENTIALS"}</span>
          <h1>{product.name}</h1>
          <div className="product-price">{money(product.unitPrice)}</div>
          <p className="product-description">
            {product.description || "Một lựa chọn thiết yếu cho mỗi ngày."}
          </p>
          <dl className="specs">
            <div>
              <dt>Mã sản phẩm</dt>
              <dd>{product.sku}</dd>
            </div>
            {product.category && (
              <div>
                <dt>Danh mục</dt>
                <dd>{product.category}</dd>
              </div>
            )}
            {Object.entries(product.attributes || {}).map(([k, v]) => (
              <div key={k}>
                <dt>{k}</dt>
                <dd>{v}</dd>
              </div>
            ))}
          </dl>
          {variants.length > 0 && (
            <div className="variant-picker">
              <span>Phiên bản</span>
              <div className="row wrap">
                {variants.map((v) => (
                  <Link
                    className={`variant ${v.id === product.id ? "selected" : ""}`}
                    key={v.id}
                    to={`/products/${v.id}`}
                  >
                    {Object.values(v.attributes || {}).join(" / ") || v.name}
                  </Link>
                ))}
              </div>
            </div>
          )}
          <div className="stock-hint">
            {stock ? (
              <>
                <span className="status-dot" />{" "}
                {stock.available > 0
                  ? `Còn ${stock.available} sản phẩm có thể đặt`
                  : "Tạm hết hàng"}
              </>
            ) : (
              "Số lượng có thể mua được xác nhận khi đặt đơn."
            )}
          </div>
          <div className="row add-to-cart">
            <Quantity value={quantity} onChange={setQuantity} />
            <button
              className="button"
              disabled={Boolean(stock && quantity > stock.available)}
              onClick={() => {
                cart.add(product, quantity);
                notify("Đã thêm sản phẩm vào giỏ hàng.");
              }}
            >
              <ShoppingBag size={17} /> Thêm vào giỏ
            </button>
          </div>
          <p className="small muted">
            Giá và tồn kho được hệ thống kiểm tra lại khi xác nhận đặt hàng.
          </p>
          <Link to="/cart" className="text-link">
            Xem giỏ hàng <ArrowRight size={16} />
          </Link>
        </div>
      </div>
    </div>
  );
}
export function AuthPage({ register = false }: { register?: boolean }) {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const notify = useToast();
  const next = params.get("next");
  const target = next?.startsWith("/") && !next.startsWith("//") ? next : null;
  useEffect(() => {
    if (user)
      navigate(target || (user.role === "ADMIN" ? "/admin" : "/"), {
        replace: true,
      });
  }, [user, navigate, target]);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(e.currentTarget);
    try {
      const email = String(form.get("email")).trim();
      const password = String(form.get("password"));
      if (register) {
        if (new TextEncoder().encode(password).length > 72)
          throw new Error("Mật khẩu tối đa 72 byte UTF-8.");
        await api("/auth/register", {
          method: "POST",
          noAuth: true,
          body: {
            fullName: String(form.get("fullName")).trim(),
            email,
            password,
          },
        });
        notify("Tạo tài khoản thành công. Vui lòng đăng nhập.");
        navigate(
          `/login${target ? `?next=${encodeURIComponent(target)}` : ""}`,
        );
      } else {
        const session = await login(email, password);
        navigate(target || (session.user.role === "ADMIN" ? "/admin" : "/"));
      }
    } catch (err) {
      setError(readableError(err));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-layout container">
      <div className="auth-editorial">
        <span className="eyebrow">WELCOME TO MONO</span>
        <h1>
          {register ? (
            <>
              Bắt đầu từ
              <br />
              <em>điều đơn giản.</em>
            </>
          ) : (
            <>
              Thật vui
              <br />
              <em>khi bạn trở lại.</em>
            </>
          )}
        </h1>
        <span className="auth-mark">m.</span>
      </div>
      <div className="auth-form">
        <span className="eyebrow">TÀI KHOẢN CỦA BẠN</span>
        <h2>{register ? "Tạo tài khoản" : "Đăng nhập"}</h2>
        <p className="muted">
          {register
            ? "Lưu địa chỉ, mua sắm và theo dõi đơn hàng."
            : "Tiếp tục trải nghiệm mua sắm của bạn."}
        </p>
        <ErrorBox message={error} />
        <form onSubmit={submit}>
          {register && (
            <label>
              Họ và tên
              <input
                name="fullName"
                required
                maxLength={100}
                autoComplete="name"
              />
            </label>
          )}
          <label>
            Email
            <input
              name="email"
              type="email"
              required
              maxLength={255}
              autoComplete="email"
            />
          </label>
          <label>
            Mật khẩu
            <input
              name="password"
              type="password"
              required
              minLength={register ? 8 : undefined}
              maxLength={72}
              autoComplete={register ? "new-password" : "current-password"}
            />
          </label>
          <button className="button full" disabled={busy}>
            {busy ? "Đang xử lý…" : register ? "Tạo tài khoản" : "Đăng nhập"}
            <ArrowRight size={17} />
          </button>
        </form>
        <p className="auth-switch">
          {register ? "Đã có tài khoản?" : "Bạn mới đến MONO?"}{" "}
          <Link
            to={`/${register ? "login" : "register"}${target ? `?next=${encodeURIComponent(target)}` : ""}`}
          >
            {register ? "Đăng nhập" : "Đăng ký ngay"}
          </Link>
        </p>
      </div>
    </div>
  );
}
export function CartPage() {
  const { items, setQuantity } = useCart();
  const total = items.reduce((s, i) => s + i.product.unitPrice * i.quantity, 0);
  return (
    <div className="container section">
      <span className="eyebrow">YOUR SELECTION</span>
      <h1 className="page-title">
        Giỏ hàng<span className="count">{items.length}</span>
      </h1>
      {!items.length ? (
        <Empty
          title="Giỏ hàng đang trống"
          text="Khám phá bộ sưu tập để tìm lựa chọn của bạn."
          action={
            <Link className="button" to="/">
              Tiếp tục mua sắm <ArrowRight size={16} />
            </Link>
          }
        />
      ) : (
        <div className="checkout-grid">
          <div>
            {items.map((item) => (
              <div className="cart-item" key={item.product.id}>
                <Link to={`/products/${item.product.id}`}>
                  <ProductArt product={item.product} />
                </Link>
                <div>
                  <span className="eyebrow">{item.product.sku}</span>
                  <Link to={`/products/${item.product.id}`}>
                    <h3>{item.product.name}</h3>
                  </Link>
                  <p>{money(item.product.unitPrice)}</p>
                  <Quantity
                    value={item.quantity}
                    onChange={(n) => setQuantity(item.product.id, n)}
                  />
                </div>
                <div className="cart-item-end">
                  <strong>
                    {money(item.product.unitPrice * item.quantity)}
                  </strong>
                  <button
                    className="icon-button"
                    aria-label={`Xóa ${item.product.name}`}
                    onClick={() => setQuantity(item.product.id, 0)}
                  >
                    <Trash2 size={17} />
                  </button>
                </div>
              </div>
            ))}
            <Link className="text-link" to="/">
              <ArrowLeft size={16} /> Tiếp tục mua sắm
            </Link>
          </div>
          <aside className="summary">
            <h3>Tóm tắt giỏ hàng</h3>
            <div className="row between">
              <span>Tạm tính</span>
              <span>{money(total)}</span>
            </div>
            <div className="summary-total">
              <span>Tổng sản phẩm</span>
              <strong>{money(total)}</strong>
            </div>
            <p className="small muted">
              Giá được xác nhận lại tại thời điểm tạo đơn.
            </p>
            <Link className="button full" to="/checkout">
              Tiến hành đặt hàng <ArrowRight size={16} />
            </Link>
          </aside>
        </div>
      )}
    </div>
  );
}
export function CheckoutPage({ pos = false }: { pos?: boolean }) {
  const { items, clear } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [address, setAddress] = useState<Address>({
    ...emptyAddress,
    recipientName: user?.fullName || "",
  });
  const [method, setMethod] = useState("ONLINE");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef<{ fingerprint: string; key: string } | null>(null);
  useEffect(() => {
    api<Address[]>("/auth/addresses")
      .then((data) => {
        setAddresses(data);
        const chosen = data.find((a) => a.defaultAddress);
        if (chosen) setAddress(chosen);
      })
      .catch(() => {});
  }, []);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const { id: _, defaultAddress: __, ...shippingAddress } = address;
    const body = {
      channel: pos ? "POS" : "WEB",
      paymentMethod: method,
      shippingAddress,
      items: items.map((i) => ({
        productId: i.product.id,
        quantity: i.quantity,
      })),
    };
    const fingerprint = JSON.stringify(body);
    if (pending.current?.fingerprint !== fingerprint)
      pending.current = { fingerprint, key: crypto.randomUUID() };
    try {
      const order = await api<Order>("/orders", {
        method: "POST",
        body: { ...body, idempotencyKey: pending.current.key },
      });
      clear();
      navigate(`${pos ? "/admin" : ""}/orders/${order.id}`);
    } catch (e) {
      setError(readableError(e));
    } finally {
      setBusy(false);
    }
  }
  if (!items.length)
    return (
      <Empty
        title="Chọn sản phẩm trước khi đặt hàng"
        action={
          <Link className="button" to={pos ? "/admin/pos" : "/"}>
            Chọn sản phẩm
          </Link>
        }
      />
    );
  const total = items.reduce((s, i) => s + i.product.unitPrice * i.quantity, 0);
  return (
    <div className={pos ? "" : "container section"}>
      <span className="eyebrow">{pos ? "POINT OF SALE" : "CHECKOUT"}</span>
      <h1 className="page-title">
        {pos ? "Xác nhận bán tại quầy" : "Hoàn tất lựa chọn của bạn."}
      </h1>
      <ErrorBox message={error} />
      <form onSubmit={submit} className="checkout-grid">
        <div className="stack">
          <section className="panel">
            <h3>
              <span className="step-number">01</span> Thông tin nhận hàng
            </h3>
            {addresses.length > 0 && (
              <label>
                Địa chỉ đã lưu
                <select
                  aria-label="Chọn địa chỉ đã lưu"
                  onChange={(e) => {
                    const selected = addresses.find(
                      (a) => a.id === Number(e.target.value),
                    );
                    if (selected) setAddress(selected);
                  }}
                  defaultValue=""
                >
                  <option value="">Chọn địa chỉ</option>
                  {addresses.map((a) => (
                    <option value={a.id} key={a.id}>
                      {a.recipientName} — {a.street}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <AddressFields value={address} onChange={setAddress} />
          </section>
          <section className="panel">
            <h3>
              <span className="step-number">02</span> Phương thức thanh toán
            </h3>
            <label
              className={`radio-card ${method === "ONLINE" ? "selected" : ""}`}
            >
              <input
                type="radio"
                name="paymentMethod"
                value="ONLINE"
                checked={method === "ONLINE"}
                onChange={() => setMethod("ONLINE")}
              />
              <div>
                <strong>VNPay sandbox</strong>
                <p>Thanh toán trực tuyến. Giữ hàng 200 giây từ khi tạo đơn.</p>
              </div>
            </label>
            <label
              className={`radio-card ${method === "COD" ? "selected" : ""}`}
            >
              <input
                type="radio"
                name="paymentMethod"
                value="COD"
                checked={method === "COD"}
                onChange={() => setMethod("COD")}
              />
              <div>
                <strong>Thanh toán khi nhận hàng</strong>
                <p>Cửa hàng xác nhận thu tiền trong quá trình giao hàng.</p>
              </div>
            </label>
          </section>
        </div>
        <aside className="summary">
          <h3>Đơn hàng của bạn</h3>
          {items.map((i) => (
            <div className="summary-item" key={i.product.id}>
              <span>
                {i.product.name}
                <small>× {i.quantity}</small>
              </span>
              <span>{money(i.product.unitPrice * i.quantity)}</span>
            </div>
          ))}
          <div className="summary-total">
            <span>Tổng dự kiến</span>
            <strong>{money(total)}</strong>
          </div>
          <button className="button full" disabled={busy}>
            {busy ? "Đang tạo đơn…" : "Xác nhận đặt hàng"}
            <ArrowRight size={16} />
          </button>
          <p className="small muted">
            Đơn được tạo theo tài khoản {user?.email}.{" "}
            {method === "ONLINE" &&
              "Bạn sẽ mở thanh toán ở trang chi tiết đơn."}
          </p>
        </aside>
      </form>
    </div>
  );
}
export function AccountPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const notify = useToast();
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [editing, setEditing] = useState<Address | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const reload = () =>
    api<Address[]>("/auth/addresses")
      .then(setAddresses)
      .catch((e) => setError(readableError(e)));
  useEffect(() => {
    reload();
  }, []);
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setBusy(true);
    setError("");
    const { id, ...body } = editing;
    try {
      await api(`/auth/addresses${id ? `/${id}` : ""}`, {
        method: id ? "PUT" : "POST",
        body,
      });
      setEditing(null);
      await reload();
      notify("Đã lưu địa chỉ.");
    } catch (e) {
      setError(readableError(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="container section">
      <span className="eyebrow">MY SPACE</span>
      <div className="section-heading">
        <h1 className="page-title">Tài khoản của tôi</h1>
        <button
          className="text-button"
          onClick={async () => {
            await logout();
            navigate("/");
          }}
        >
          <LogOut size={16} /> Đăng xuất
        </button>
      </div>
      <div className="account-grid">
        <aside className="panel account-profile">
          <span className="avatar large-avatar">
            {user?.fullName.charAt(0)}
          </span>
          <h2>{user?.fullName}</h2>
          <p>{user?.email}</p>
          <p className="small muted">Tham gia {dateTime(user?.createdAt)}</p>
          <Link className="button secondary full" to="/orders">
            Đơn hàng của tôi <ArrowRight size={16} />
          </Link>
        </aside>
        <section>
          <div className="section-heading">
            <h2>Sổ địa chỉ</h2>
            <button
              className="button small"
              disabled={addresses.length >= 10}
              onClick={() =>
                setEditing({
                  ...emptyAddress,
                  defaultAddress: addresses.length === 0,
                })
              }
            >
              <Plus size={16} /> Thêm địa chỉ
            </button>
          </div>
          <ErrorBox message={error} />
          {addresses.length ? (
            <div className="stack">
              {addresses.map((a) => (
                <div className="panel address-card" key={a.id}>
                  <div>
                    <strong>{a.recipientName}</strong>
                    {a.defaultAddress && (
                      <span className="badge">Mặc định</span>
                    )}
                    <p>{a.phone}</p>
                    <p>
                      {[a.street, a.ward, a.district, a.province].join(", ")}
                    </p>
                  </div>
                  <div className="row">
                    <button
                      className="text-button"
                      onClick={() => setEditing(a)}
                    >
                      Sửa
                    </button>
                    <button
                      className="icon-button"
                      aria-label={`Xóa địa chỉ ${a.street}`}
                      onClick={async () => {
                        if (!confirm("Xóa địa chỉ này?")) return;
                        try {
                          await api(`/auth/addresses/${a.id}`, {
                            method: "DELETE",
                          });
                          await reload();
                        } catch (e) {
                          setError(readableError(e));
                        }
                      }}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <Empty
              title="Chưa có địa chỉ"
              text="Lưu địa chỉ để đặt hàng thuận tiện hơn."
            />
          )}
        </section>
      </div>
      {editing && (
        <Modal
          title={editing.id ? "Cập nhật địa chỉ" : "Địa chỉ mới"}
          onClose={() => setEditing(null)}
        >
          <form onSubmit={save}>
            <ErrorBox message={error} />
            <AddressFields value={editing} onChange={setEditing} />
            <label className="checkbox">
              <input
                type="checkbox"
                checked={Boolean(editing.defaultAddress)}
                onChange={(e) =>
                  setEditing({ ...editing, defaultAddress: e.target.checked })
                }
              />{" "}
              Đặt làm địa chỉ mặc định
            </label>
            <button className="button full" disabled={busy}>
              Lưu địa chỉ
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}
