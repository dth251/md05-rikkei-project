import type { Product } from "../types";
import shirt from "../assets/products/shirt.jpg";
import blueShirt from "../assets/products/shirt-blue.jpg";
import redShirt from "../assets/products/shirt-red.jpg";
import blackShirt from "../assets/products/shirt-black.jpg";
import hoodie from "../assets/products/hoodie.jpg";
import mug from "../assets/products/mug.jpg";
import backpack from "../assets/products/backpack.jpg";
import aoDai from "../assets/products/ao-dai.jpg";
import phone from "../assets/products/phone.jpg";
import parcel from "../assets/products/parcel.jpg";

export const fallbackProductImage = parcel;

function normalized(value: string) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/đ/g, "d");
}

// API chưa có trường ảnh: chọn ảnh minh họa từ tên, danh mục và màu biến thể.
// Không gán ảnh vật phẩm cụ thể cho fixture không có thông tin sản phẩm.
export function productIllustration(product?: Partial<Product>) {
  const name = normalized(product?.name || "");
  const category = normalized(product?.category || "");
  const attributes = Object.fromEntries(
    Object.entries(product?.attributes || {}).map(([key, value]) => [
      normalized(key),
      normalized(value),
    ]),
  );
  if (/hoodie|ao khoac/.test(name)) return hoodie;
  if (/ao dai/.test(name)) return aoDai;
  if (/dien thoai|dienthoai|iphone|smartphone/.test(`${name} ${category}`))
    return phone;
  if (/backpack|ba lo|balo/.test(name)) return backpack;
  if (/\bmug\b|\bcoc\b|\btach\b/.test(name)) return mug;
  if (
    /\bao\b|shirt/.test(name) ||
    /clothing|fashion|thoi\s?trang/.test(category) ||
    Boolean(attributes.size && attributes.color)
  ) {
    const color =
      attributes.color || attributes["mau sac"] || attributes.mau || name;
    if (/blue|xanh/.test(color)) return blueShirt;
    if (/\bred\b|\bdo\b/.test(color)) return redShirt;
    if (/black|\bden\b/.test(color)) return blackShirt;
    return shirt;
  }
  return parcel;
}
