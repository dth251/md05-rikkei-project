import { expect, test, type Page } from "@playwright/test";

const products = [
  ["Áo cotton trắng", "shirt", {}],
  ["Áo cotton xanh", "shirt-blue", { color: "blue", size: "m" }],
  ["Áo cotton đỏ", "shirt-red", { color: "red", size: "l" }],
  ["Áo cotton đen", "shirt-black", { color: "black", size: "s" }],
  ["Áo hoodie zip", "hoodie", {}],
  ["Áo dài", "ao-dai", {}],
  ["Điện thoại", "phone", {}],
  ["Demo Backpack", "backpack", {}],
  ["Demo Mug", "mug", {}],
  ["Fixture chưa mô tả", "parcel", {}],
].map(([name, image, attributes], index) => ({
  id: index + 1,
  name: String(name),
  image: String(image),
  sku: `IMAGE-${index}`,
  unitPrice: 125000,
  category: "",
  brand: "MONO",
  description: "Sản phẩm minh họa",
  active: true,
  parentId: index > 0 && index < 4 ? 1 : null,
  attributes,
}));

async function catalog(page: Page) {
  await page.route("**/api/products**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    const id = Number(path.split("/")[3]);
    const data = path.endsWith("/variants")
      ? products.slice(1, 4)
      : id
        ? products.find((p) => p.id === id)
        : {
            content: products,
            totalElements: products.length,
            totalPages: 1,
            page: 0,
            size: 12,
          };
    await route.fulfill({ json: { success: true, data } });
  });
}

test("catalog images load and remain correct in product details, variants and cart", async ({
  page,
}, info) => {
  await catalog(page);
  await page.goto("/");
  for (const product of products) {
    const image = page.getByRole("img", {
      name: `Ảnh minh họa ${product.name}`,
      exact: true,
    });
    await image.scrollIntoViewIfNeeded();
    await expect(image).toHaveAttribute(
      "src",
      new RegExp(`/${product.image}(?:-|\\.jpg)`),
    );
    await expect
      .poll(() =>
        image.evaluate(
          (element: HTMLImageElement) =>
            element.complete && element.naturalWidth > 0,
        ),
      )
      .toBe(true);
  }
  await page.screenshot({
    path: info.outputPath("product-illustrations.png"),
    fullPage: true,
  });
  await page.getByRole("link", { name: "Áo cotton xanh", exact: true }).click();
  await expect(page.locator(".product-detail img")).toHaveAttribute(
    "src",
    /shirt-blue/,
  );
  await page.locator('.variant[href="/products/4"]').click();
  await expect(page.locator(".product-detail img")).toHaveAttribute(
    "src",
    /shirt-black/,
  );
  await page.getByRole("button", { name: "Thêm vào giỏ" }).click();
  await page.goto("/cart");
  await expect(page.locator(".cart-item img")).toHaveAttribute(
    "src",
    /shirt-black/,
  );
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true);
});

test("a failed product image falls back to a local parcel image", async ({
  page,
}) => {
  await catalog(page);
  await page.route("**/shirt.jpg*", (route) =>
    route.request().resourceType() === "image"
      ? route.abort()
      : route.continue(),
  );
  await page.goto("/products/1");
  const image = page.locator(".product-detail img");
  await expect(image).toHaveAttribute("src", /parcel/);
  await expect
    .poll(() =>
      image.evaluate(
        (element: HTMLImageElement) =>
          element.complete && element.naturalWidth > 0,
      ),
    )
    .toBe(true);
});

test("a failed fallback image still has an accessible illustration without a broken image", async ({
  page,
}) => {
  await catalog(page);
  await page.route(/\/(?:shirt|parcel)\.jpg(?:\?.*)?$/, (route) =>
    route.request().resourceType() === "image"
      ? route.abort()
      : route.continue(),
  );
  await page.goto("/products/1");
  await expect(
    page.locator(".product-detail svg[role=img]"),
  ).toHaveAccessibleName("Ảnh minh họa Áo cotton trắng");
  await expect(page.locator(".product-detail img")).toHaveCount(0);
});
