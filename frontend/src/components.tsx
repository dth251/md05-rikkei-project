import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  Package,
  X,
  Minus,
  Plus,
  LoaderCircle,
  AlertCircle,
} from "lucide-react";
import type { Product, Address } from "./types";
import { money, orderLabels, returnLabels } from "./lib/workflow";
import {
  fallbackProductImage,
  productIllustration,
} from "./lib/product-images";

export function ErrorBox({ message }: { message: string }) {
  return message ? (
    <div className="notice error" role="alert">
      <AlertCircle size={18} />
      <span>{message}</span>
    </div>
  ) : null;
}
export function Loading() {
  return (
    <div className="loading" role="status">
      <LoaderCircle size={22} className="spin" /> Đang tải…
    </div>
  );
}
export function Empty({
  title,
  text,
  action,
}: {
  title: string;
  text?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <Package size={36} strokeWidth={1} />
      <h3>{title}</h3>
      <p>{text}</p>
      {action}
    </div>
  );
}
export function Badge({ value }: { value: string }) {
  return (
    <span className={`badge ${value.toLowerCase()}`}>
      {orderLabels[value] ||
        returnLabels[value] ||
        (
          {
            SUCCESS: "Đã thu tiền",
            FAILED: "Thất bại",
            PENDING: "Chưa thanh toán",
            REQUIRED: "Cần hoàn tiền",
            NONE: "Không",
          } as Record<string, string>
        )[value] ||
        value}
    </span>
  );
}
export function ProductArt({
  product,
  large = false,
}: {
  product?: Partial<Product>;
  large?: boolean;
}) {
  const source = productIllustration(product);
  const [failed, setFailed] = useState<string[]>([]);
  const image = failed.includes(source) ? fallbackProductImage : source;
  const alt = `Ảnh minh họa ${product?.name || "sản phẩm"}`;
  return (
    <div className={`product-art ${large ? "large" : ""}`}>
      {failed.includes(image) ? (
        <Package
          role="img"
          aria-label={alt}
          size={large ? 180 : 100}
          strokeWidth={0.7}
        />
      ) : (
        <img
          src={image}
          alt={alt}
          width={1024}
          height={1024}
          loading={large ? "eager" : "lazy"}
          decoding="async"
          onError={() => setFailed((previous) => [...previous, image])}
        />
      )}
      <span className="art-label" aria-hidden="true">
        Ảnh minh họa
      </span>
    </div>
  );
}
export function ProductCard({ product }: { product: Product }) {
  return (
    <Link
      className="product-card"
      to={`/products/${product.id}`}
      aria-label={product.name}
    >
      <ProductArt product={product} />
      <div className="product-card-meta">
        <span className="eyebrow">
          {product.brand || product.category || "MONO ESSENTIALS"}
        </span>
        <h3>{product.name}</h3>
        <div className="row between">
          <span>{money(product.unitPrice)}</span>
          <ArrowRight size={17} />
        </div>
      </div>
    </Link>
  );
}
export function Quantity({
  value,
  onChange,
}: {
  value: number;
  onChange: (n: number) => void;
}) {
  return (
    <div className="quantity">
      <button
        type="button"
        aria-label="Giảm số lượng"
        disabled={value <= 1}
        onClick={() => onChange(value - 1)}
      >
        <Minus size={14} />
      </button>
      <span>{value}</span>
      <button
        type="button"
        aria-label="Tăng số lượng"
        disabled={value >= 10000}
        onClick={() => onChange(value + 1)}
      >
        <Plus size={14} />
      </button>
    </div>
  );
}
export function Pagination({
  page,
  pages,
  onChange,
}: {
  page: number;
  pages: number;
  onChange: (page: number) => void;
}) {
  return pages > 1 ? (
    <div className="pagination">
      <button
        className="button secondary small"
        disabled={!page}
        onClick={() => onChange(page - 1)}
      >
        Trước
      </button>
      <span>
        {page + 1} / {pages}
      </span>
      <button
        className="button secondary small"
        disabled={page + 1 >= pages}
        onClick={() => onChange(page + 1)}
      >
        Sau
      </button>
    </div>
  ) : null;
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
    return () => ref.current?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className="modal"
      onCancel={onClose}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal-head">
        <h2>{title}</h2>
        <button
          type="button"
          className="icon-button"
          aria-label="Đóng"
          onClick={onClose}
        >
          <X />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export const emptyAddress: Address = {
  recipientName: "",
  phone: "",
  street: "",
  ward: "",
  district: "",
  province: "",
  postalCode: "",
};
export function AddressFields({
  value,
  onChange,
}: {
  value: Address;
  onChange: (address: Address) => void;
}) {
  const fields: [keyof Address, string, string, number][] = [
    ["recipientName", "Người nhận", "text", 100],
    ["phone", "Số điện thoại", "tel", 16],
    ["street", "Địa chỉ cụ thể", "text", 255],
    ["ward", "Phường / xã", "text", 100],
    ["district", "Quận / huyện", "text", 100],
    ["province", "Tỉnh / thành phố", "text", 100],
    ["postalCode", "Mã bưu chính (tùy chọn)", "text", 10],
  ];
  return (
    <div className="form-grid">
      {fields.map(([key, label, type, maxLength]) => (
        <label key={key} className={key === "street" ? "full" : ""}>
          {label}
          <input
            name={key}
            type={type}
            maxLength={maxLength}
            required={key !== "postalCode"}
            pattern={key === "phone" ? "\\+?[0-9]{9,15}" : undefined}
            autoComplete={key === "phone" ? "tel" : undefined}
            value={String(value[key] || "")}
            onChange={(e) => onChange({ ...value, [key]: e.target.value })}
          />
        </label>
      ))}
    </div>
  );
}
export function useDebounced<T>(value: T, delay = 350) {
  const [result, setResult] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setResult(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return result;
}
