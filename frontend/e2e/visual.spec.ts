import { test, expect } from "@playwright/test";

const products = [
  "Áo cotton thiết yếu",
  "Balô hằng ngày",
  "Cốc gốm tối giản",
  "Áo hoodie zip",
].map((name, i) => ({
  id: i + 1,
  name,
  sku: `MONO-${i}`,
  brand: "MONO",
  category: "Essentials",
  description: "",
  unitPrice: 125000 + i * 50000,
  active: true,
  parentId: null,
  attributes: {},
}));
test("visual storefront, loading contract and responsive viewport", async ({
  page,
}, info) => {
  await page.route("**/api/products?**", (route) =>
    route.fulfill({
      json: {
        success: true,
        data: {
          content: products,
          totalElements: 4,
          page: 0,
          size: 12,
          totalPages: 1,
        },
      },
    }),
  );
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Áo cotton thiết yếu", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true);
  await expect
    .poll(() =>
      page
        .locator(".product-art img")
        .evaluateAll((images) =>
          images.every(
            (image) =>
              (image as HTMLImageElement).complete &&
              (image as HTMLImageElement).naturalWidth > 0,
          ),
        ),
    )
    .toBe(true);
  await page.screenshot({
    path: info.outputPath("storefront.png"),
    fullPage: true,
  });
});
test("visual administrator workspace and responsive navigation", async ({
  page,
}, info) => {
  await page.addInitScript(() =>
    sessionStorage.setItem(
      "md05.session",
      JSON.stringify({
        accessToken: "fixture",
        refreshToken: "fixture",
        user: {
          id: 1,
          role: "ADMIN",
          fullName: "MONO Admin",
          email: "admin@example.test",
        },
      }),
    ),
  );
  await page.route("**/api/**", async (route) => {
    const isProduct =
      new URL(route.request().url()).pathname === "/api/products";
    await route.fulfill({
      json: {
        success: true,
        data: {
          content: isProduct
            ? products
            : [
                {
                  id: 100,
                  userId: 1,
                  channel: "WEB",
                  status: "CONFIRMED",
                  paymentMethod: "ONLINE",
                  totalAmount: 125000,
                  createdAt: "2026-10-15T12:00:00Z",
                },
              ],
          totalElements: isProduct ? 4 : 1,
          totalPages: 1,
          page: 0,
          size: 6,
        },
      },
    });
  });
  await page.goto("/admin");
  await expect(
    page.getByRole("heading", { name: "Tổng quan cửa hàng" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "#100", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true);
  await page.screenshot({ path: info.outputPath("admin.png"), fullPage: true });
});
