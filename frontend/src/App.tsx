import { useState } from "react";
import {
  Link,
  NavLink,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  ShoppingBag,
  UserRound,
  ArrowUpRight,
  LayoutDashboard,
  Package,
  Boxes,
  ClipboardList,
  Store,
  UsersRound,
  CreditCard,
  LogOut,
  Menu,
  X,
  Bell,
} from "lucide-react";
import { useAuth, useCart } from "./context";
import {
  ShopHome,
  ProductDetail,
  AuthPage,
  CartPage,
  CheckoutPage,
  AccountPage,
} from "./pages/shop";
import { OrdersPage, OrderDetail } from "./pages/orders";
import {
  Dashboard,
  ProductsAdmin,
  InventoryAdmin,
  CustomersAdmin,
  NotificationsAdmin,
  PosAdmin,
  PaymentsAdmin,
} from "./pages/admin";

function RequireAuth({ admin = false }: { admin?: boolean }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user)
    return (
      <Navigate
        to={`/login?next=${encodeURIComponent(location.pathname)}`}
        replace
      />
    );
  if (admin && user.role !== "ADMIN") return <Navigate to="/" replace />;
  return <Outlet />;
}
function ShopLayout() {
  const { user } = useAuth();
  const { items } = useCart();
  const [menu, setMenu] = useState(false);
  return (
    <>
      <div className="announcement">
        Đơn giản trong lựa chọn. Trọn vẹn trong trải nghiệm.
        <span>MONO — EST. 2026</span>
      </div>
      <header className="shop-header">
        <Link to="/" className="wordmark" aria-label="MONO trang chủ">
          mono<span>®</span>
        </Link>
        <nav
          className={menu ? "shop-nav open" : "shop-nav"}
          onClick={() => setMenu(false)}
        >
          <NavLink to="/" end>
            Cửa hàng
          </NavLink>
          {user && <NavLink to="/orders">Đơn hàng</NavLink>}
          <Link to="/#collection">Bộ sưu tập</Link>
          {user?.role === "ADMIN" && (
            <Link to="/admin">
              Quản trị <ArrowUpRight size={13} />
            </Link>
          )}
        </nav>
        <div className="header-actions">
          <Link
            className="icon-button"
            to={user ? "/account" : "/login"}
            aria-label={user ? "Tài khoản" : "Đăng nhập"}
          >
            <UserRound size={20} />
          </Link>
          <Link className="cart-link" to="/cart" aria-label="Giỏ hàng">
            <ShoppingBag size={20} />
            <span>{items.reduce((s, i) => s + i.quantity, 0)}</span>
          </Link>
          <button
            className="icon-button mobile-menu"
            aria-label="Menu"
            onClick={() => setMenu(!menu)}
          >
            {menu ? <X /> : <Menu />}
          </button>
        </div>
      </header>
      <main className="shop-main">
        <Outlet />
      </main>
      <footer className="footer">
        <div>
          <Link className="wordmark" to="/">
            mono<span>®</span>
          </Link>
          <p>
            Những điều thiết yếu.
            <br />
            Được chọn một cách có chủ đích.
          </p>
        </div>
        <div>
          <span className="eyebrow">KHÁM PHÁ</span>
          <Link to="/">Cửa hàng</Link>
          <Link to="/orders">Đơn hàng của tôi</Link>
          <Link to="/account">Tài khoản & địa chỉ</Link>
        </div>
        <div>
          <span className="eyebrow">MUA SẮM AN TÂM</span>
          <p>Thanh toán VNPay sandbox / COD</p>
          <p>Theo dõi giao hàng và yêu cầu trả đơn</p>
          <a href="/swagger-ui/" target="_blank" rel="noreferrer">
            Tài liệu API <ArrowUpRight size={12} />
          </a>
        </div>
        <div className="footer-bottom">
          <span>© 2026 MONO / MD05</span>
          <span>Designed with less. Built for more.</span>
        </div>
      </footer>
    </>
  );
}
const adminNav = [
  ["/admin", "Tổng quan", LayoutDashboard],
  ["/admin/products", "Sản phẩm", Package],
  ["/admin/inventory", "Tồn kho", Boxes],
  ["/admin/orders", "Đơn hàng", ClipboardList],
  ["/admin/payments", "Thanh toán", CreditCard],
  ["/admin/pos", "Bán tại quầy", Store],
  ["/admin/customers", "Khách hàng", UsersRound],
  ["/admin/notifications", "Thông báo", Bell],
] as const;
function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  return (
    <div className="admin-layout">
      <aside className={open ? "admin-sidebar open" : "admin-sidebar"}>
        <Link to="/admin" className="wordmark inverse">
          mono<span>®</span>
        </Link>
        <span className="admin-label">WORKSPACE / ADMIN</span>
        <nav>
          {adminNav.map(([to, label, Icon]) => (
            <NavLink
              key={to}
              to={to}
              end={to === "/admin"}
              onClick={() => setOpen(false)}
            >
              <Icon size={18} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <Link to="/">
            <ArrowUpRight size={17} /> Xem cửa hàng
          </Link>
          <button
            onClick={async () => {
              await logout();
              navigate("/login");
            }}
          >
            <LogOut size={17} /> Đăng xuất
          </button>
          <div className="admin-user">
            <span className="avatar">{user?.fullName?.charAt(0) || "A"}</span>
            <div>
              <strong>{user?.fullName}</strong>
              <small>Quản trị viên</small>
            </div>
          </div>
        </div>
      </aside>
      {open && (
        <button
          className="sidebar-overlay"
          aria-label="Đóng menu"
          onClick={() => setOpen(false)}
        />
      )}
      <div className="admin-body">
        <header className="admin-topbar">
          <button
            className="icon-button mobile-menu"
            aria-label="Menu quản trị"
            onClick={() => setOpen(!open)}
          >
            <Menu />
          </button>
          <span>MONO MANAGEMENT</span>
          <div className="row">
            <span className="status-dot" />
            {user?.email}
          </div>
        </header>
        <main className="admin-main">
          <Outlet />
        </main>
        <footer className="admin-footer">
          MONO ADMIN<span>Hệ thống quản lý bán hàng đa kênh</span>
        </footer>
      </div>
    </div>
  );
}
export default function App() {
  return (
    <Routes>
      <Route element={<ShopLayout />}>
        <Route index element={<ShopHome />} />
        <Route path="products/:id" element={<ProductDetail />} />
        <Route path="login" element={<AuthPage />} />
        <Route path="register" element={<AuthPage register />} />
        <Route path="cart" element={<CartPage />} />
        <Route element={<RequireAuth />}>
          <Route path="checkout" element={<CheckoutPage />} />
          <Route path="account" element={<AccountPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:id" element={<OrderDetail />} />
        </Route>
        <Route
          path="*"
          element={
            <div className="container section">
              <h1>Trang không tồn tại.</h1>
              <Link className="button" to="/">
                Về cửa hàng
              </Link>
            </div>
          }
        />
      </Route>
      <Route element={<RequireAuth admin />}>
        <Route path="admin" element={<AdminLayout />}>
          <Route index element={<Dashboard />} />
          <Route path="products" element={<ProductsAdmin />} />
          <Route path="inventory" element={<InventoryAdmin />} />
          <Route path="payments" element={<PaymentsAdmin />} />
          <Route path="orders" element={<OrdersPage admin />} />
          <Route path="orders/:id" element={<OrderDetail admin />} />
          <Route path="pos" element={<PosAdmin />} />
          <Route path="customers" element={<CustomersAdmin />} />
          <Route path="notifications" element={<NotificationsAdmin />} />
        </Route>
      </Route>
    </Routes>
  );
}
