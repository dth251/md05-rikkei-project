import { test, expect, type Page } from "@playwright/test";

test("inventory search hides stale rows before accepting a stock receipt", async ({
  page,
}) => {
  const app = await fixture(page, { role: "ADMIN" });
  await page.route(/\/api\/products\?/, async (route) => {
    const searched = new URL(route.request().url()).searchParams.get("q");
    if (searched) await new Promise((resolve) => setTimeout(resolve, 1000));
    const selected = searched
      ? { ...product, id: 20, name: "Sản phẩm mới riêng", sku: "NEW-20" }
      : product;
    await route.fulfill({ json: { success: true, data: summary(selected) } });
  });
  await app.login();
  await page.goto("/admin/inventory");
  await expect(
    page.getByRole("row").filter({ hasText: product.name }),
  ).toBeVisible();
  await page.getByLabel("Tìm sản phẩm trong kho").fill("Sản phẩm mới riêng");
  await expect(
    page.getByRole("button", { name: "Nhập kho", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("row")
    .filter({ hasText: "Sản phẩm mới riêng" })
    .getByRole("button", { name: "Nhập kho", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Nhập kho — Sản phẩm mới riêng" }),
  ).toBeVisible();
  await expect(page.locator("dialog")).toContainText("NEW-20");
});

const product = {
  id: 10,
  sku: "MONO-TEST",
  name: "Áo cotton thiết yếu",
  description: "Một lựa chọn gọn gàng cho mỗi ngày.",
  unitPrice: 125000,
  category: "Thời trang",
  brand: "MONO",
  active: true,
  parentId: null,
  attributes: {},
  createdAt: "2026-10-15T00:00:00Z",
};
const address = {
  id: 1,
  recipientName: "Khách kiểm thử",
  phone: "0901234567",
  street: "12 Nguyễn Trãi",
  ward: "Phường 1",
  district: "Quận 1",
  province: "TP HCM",
  postalCode: "",
  defaultAddress: true,
};
const summary = (order: any) => ({
  content: [order],
  page: 0,
  size: 15,
  totalElements: 1,
  totalPages: 1,
});
function session(role = "USER") {
  return {
    accessToken: "fixture-token",
    refreshToken: "fixture-refresh",
    expiresIn: 900,
    user: {
      id: 1,
      role,
      fullName: "Khách kiểm thử",
      email: "fixture@example.test",
      createdAt: "2026-10-15T00:00:00Z",
    },
  };
}
async function fixture(
  page: Page,
  options: {
    role?: string;
    status?: string;
    method?: string;
    expired?: boolean;
  } = {},
) {
  const role = options.role || "USER";
  const order: any = {
    id: 100,
    userId: 1,
    status: options.status || "PENDING_PAYMENT",
    channel: "WEB",
    totalAmount: 125000,
    createdAt: new Date(
      Date.now() - (options.expired ? 210000 : 1000),
    ).toISOString(),
    paymentMethod: options.method || "ONLINE",
    items: [
      {
        productId: 10,
        sku: product.sku,
        name: product.name,
        unitPrice: 125000,
        quantity: 1,
      },
    ],
    shippingAddress: address,
  };
  const payment: any = {
    id: 200,
    orderId: 100,
    amount: 125000,
    status: options.expired ? "SUCCESS" : "PENDING",
    reservationExpired: Boolean(options.expired),
    refundStatus: options.expired ? "REQUIRED" : "NONE",
    paymentMethod: order.paymentMethod,
  };
  let returned: any = null;
  const calls: { method: string; path: string; body: any }[] = [];
  await page.route("**/api/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname,
      method = request.method();
    const body = request.postDataJSON();
    calls.push({ method, path, body });
    let data: any = null,
      status = 200;
    if (path === "/api/auth/login" || path === "/api/auth/refresh")
      data = session(role);
    else if (path === "/api/auth/addresses")
      data = method === "GET" ? [address] : { ...body, id: 2 };
    else if (path === "/api/auth/me") data = session(role).user;
    else if (path === "/api/auth/logout") {
      await route.fulfill({ status: 204 });
      return;
    } else if (path === "/api/products")
      data = method === "GET" ? summary(product) : { ...body, id: 11 };
    else if (/\/products\/\d+\/variants$/.test(path))
      data =
        method === "GET"
          ? [
              {
                ...product,
                id: 12,
                name: "Áo cotton màu đen",
                parentId: 10,
                attributes: { color: "Black", size: "M" },
              },
            ]
          : { ...body.product, id: 13, attributes: body.attributes };
    else if (/\/products\/\d+$/.test(path)) data = product;
    else if (path === "/api/inventory/stocks")
      data = {
        productId: body.productId,
        available: body.quantity + 9,
        reserved: 1,
      };
    else if (/\/inventory\/\d+$/.test(path))
      data = { productId: 10, available: 9, reserved: 1 };
    else if (path === "/api/orders") {
      if (method === "POST") {
        Object.assign(order, {
          paymentMethod: body.paymentMethod,
          channel: body.channel,
          shippingAddress: body.shippingAddress,
          status:
            body.paymentMethod === "COD" ? "CONFIRMED" : "PENDING_PAYMENT",
        });
        payment.paymentMethod = body.paymentMethod;
        data = order;
        status = 201;
      } else data = summary(order);
    } else if (/\/orders\/\d+\/ship$/.test(path)) {
      order.status = "SHIPPING";
      order.shipment = { ...body, shippedAt: new Date().toISOString() };
      data = order;
    } else if (/\/orders\/\d+\/complete$/.test(path)) {
      if (order.paymentMethod === "COD" && !order.codPaidAt) {
        status = 409;
        data = null;
      } else {
        order.status = "COMPLETED";
        data = order;
      }
    } else if (/\/orders\/\d+\/return\/decision$/.test(path)) {
      returned.status = body.approved ? "APPROVED" : "REJECTED";
      data = returned;
    } else if (/\/orders\/\d+\/return\/receive$/.test(path)) {
      returned.status = "REFUND_PENDING";
      returned.warehouseReceipt = body.warehouseReceipt;
      returned.restock = body.restock;
      payment.status = "SUCCESS";
      payment.refundStatus = "REQUIRED";
      data = returned;
    } else if (/\/orders\/\d+\/return$/.test(path)) {
      if (method === "POST") {
        returned = {
          orderId: 100,
          status: "REQUESTED",
          reason: body.reason,
          amount: 125000,
          requestedAt: new Date().toISOString(),
        };
        data = returned;
      } else if (!returned) status = 404;
      else data = returned;
    } else if (/\/orders\/\d+$/.test(path)) data = order;
    else if (/\/payments\/by-order\/\d+$/.test(path)) data = payment;
    else if (/\/payments\/\d+\/cod\/collect$/.test(path)) {
      payment.status = "SUCCESS";
      order.codPaidAt = new Date().toISOString();
      data = payment;
    } else if (/\/payments\/\d+\/cod\/refund$/.test(path)) {
      payment.refundStatus = "REFUNDED";
      returned.status = "REFUNDED";
      data = payment;
    } else {
      status = 404;
    }
    await route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify({
        success: status < 400,
        data,
        message:
          status >= 400 ? "Fixture state does not allow operation" : "OK",
      }),
    });
  });
  return {
    order,
    payment,
    calls,
    login: async () => {
      await page.goto("/login");
      await page
        .getByLabel("Email", { exact: true })
        .fill("fixture@example.test");
      await page
        .getByLabel("Mật khẩu", { exact: true })
        .fill("FixturePassword!");
      await page
        .getByRole("button", { name: "Đăng nhập", exact: true })
        .click();
      await expect(page).not.toHaveURL(/\/login/);
    },
  };
}
test("storefront renders real contract products, filters, variants and cart without horizontal overflow", async ({
  page,
}) => {
  await fixture(page);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /Ít hơn/ })).toBeVisible();
  await page.getByRole("link", { name: /Áo cotton thiết yếu/ }).click();
  await expect(
    page.getByRole("heading", { name: product.name, exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "Black / M" })).toBeVisible();
  await page.getByRole("button", { name: /Thêm vào giỏ/ }).click();
  await page.goto("/cart");
  await expect(page.getByRole("heading", { name: /Giỏ hàng/ })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Tiến hành đặt hàng" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true);
});
test("protected admin routes require ADMIN and customer session cannot open the workspace", async ({
  page,
}) => {
  const app = await fixture(page);
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/login/);
  await app.login();
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/$/);
  await expect(
    page.getByRole("heading", { name: "Tổng quan cửa hàng" }),
  ).toHaveCount(0);
});
test("customer checkout creates COD once with address and stable idempotency key", async ({
  page,
}) => {
  const app = await fixture(page);
  await app.login();
  await page.goto("/products/10");
  await page.getByRole("button", { name: /Thêm vào giỏ/ }).click();
  await page.goto("/checkout");
  await expect(page.getByLabel("Người nhận", { exact: true })).toHaveValue(
    address.recipientName,
  );
  await page.getByRole("radio", { name: /Thanh toán khi nhận hàng/ }).check();
  await page.getByRole("button", { name: "Xác nhận đặt hàng" }).click();
  await expect(page).toHaveURL(/\/orders\/100$/);
  const creates = app.calls.filter(
    (c) => c.path === "/api/orders" && c.method === "POST",
  );
  expect(creates).toHaveLength(1);
  expect(creates[0].body.paymentMethod).toBe("COD");
  expect(creates[0].body.shippingAddress.phone).toBe(address.phone);
  expect(creates[0].body.idempotencyKey).toBeTruthy();
  await expect(
    page.getByRole("button", { name: /Thanh toán VNPay/ }),
  ).toHaveCount(0);
});
test("expired successful capture displays refund requirement and offers no checkout or shipping", async ({
  page,
}) => {
  const app = await fixture(page, {
    role: "ADMIN",
    expired: true,
    status: "EXPIRED",
  });
  await app.login();
  await page.goto("/admin/orders/100");
  await expect(
    page.getByText("Đã ghi nhận thu tiền. Khoản này đang cần hoàn lại."),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Yêu cầu hoàn VNPay" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Giao hàng", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: /Thanh toán VNPay/ }),
  ).toHaveCount(0);
});
test("COD admin follows shipping then actual collection acknowledgement before completion", async ({
  page,
}) => {
  const app = await fixture(page, {
    role: "ADMIN",
    method: "COD",
    status: "CONFIRMED",
  });
  await app.login();
  await page.goto("/admin/orders/100");
  await expect(
    page.getByRole("button", { name: "Ghi nhận thu COD" }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Giao hàng", exact: true }).click();
  await page
    .getByLabel("Đơn vị vận chuyển", { exact: true })
    .fill("MONO Delivery");
  await page.getByLabel("Mã vận đơn", { exact: true }).fill("TRACK-100");
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Hoàn tất đơn", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Ghi nhận thu COD" }).click();
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await page.getByRole("button", { name: "Hoàn tất đơn", exact: true }).click();
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(page.locator(".page-title .badge")).toHaveText("Hoàn tất");
  expect(
    app.calls
      .filter((c) => /\/(ship|collect|complete)$/.test(c.path))
      .map((c) => c.path.split("/").pop()),
  ).toEqual(["ship", "collect", "complete"]);
});
test("full return follows request approval receipt and refund in separate steps", async ({
  page,
}) => {
  const app = await fixture(page, {
    role: "ADMIN",
    method: "COD",
    status: "COMPLETED",
  });
  app.payment.status = "SUCCESS";
  await app.login();
  await page.goto("/admin/orders/100");
  await page.getByRole("button", { name: "Tạo yêu cầu trả hàng" }).click();
  await page
    .getByLabel("Lý do trả toàn bộ đơn")
    .fill("Kiểm thử trả toàn bộ đơn");
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Nhận hàng trả", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Duyệt trả hàng", exact: true })
    .click();
  await page.getByLabel("Ghi chú quyết định").fill("Đồng ý");
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await page
    .getByRole("button", { name: "Nhận hàng trả", exact: true })
    .click();
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(
    page.getByText("Đã nhận hàng, đang chờ kết quả hoàn tiền.", {
      exact: false,
    }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Ghi nhận chi hoàn COD" }).click();
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(
    page.locator(".badge").filter({ hasText: "Đã hoàn tiền" }).first(),
  ).toBeVisible();
});
test("admin can create catalog product and input stock with idempotency, preserving minimal layout", async ({
  page,
}) => {
  const app = await fixture(page, { role: "ADMIN" });
  await app.login();
  await page.goto("/admin/products");
  await page.getByRole("button", { name: "Thêm sản phẩm" }).click();
  await page
    .getByLabel("Tên sản phẩm", { exact: true })
    .fill("Túi vải tối giản");
  await page.getByRole("button", { name: "Lưu sản phẩm", exact: true }).click();
  expect(
    app.calls.some(
      (c) =>
        c.path === "/api/products" &&
        c.method === "POST" &&
        c.body.name === "Túi vải tối giản",
    ),
  ).toBe(true);
  await page.goto("/admin/inventory");
  await page
    .getByRole("button", { name: "Nhập kho", exact: true })
    .first()
    .click();
  await page.getByLabel("Số lượng nhập").fill("20");
  await page.getByRole("button", { name: "Xác nhận nhập kho" }).click();
  expect(
    app.calls.find((c) => c.path === "/api/inventory/stocks")?.body
      .idempotencyKey,
  ).toBeTruthy();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true);
});
