import { test, expect, type Page } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const privateFile = process.env.FRONTEND_LIVE_CREDENTIALS;
const credentials = privateFile
  ? JSON.parse(readFileSync(privateFile, "utf8"))
  : null;
test.skip(
  !credentials,
  "Set FRONTEND_LIVE_CREDENTIALS to a private fixture file.",
);

async function login(page: Page, email: string, password: string) {
  await page.goto("/");
  await page.evaluate(() => sessionStorage.removeItem("md05.session"));
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/);
}
async function confirm(page: Page) {
  await page.getByRole("button", { name: "Xác nhận", exact: true }).click();
  await expect(page.locator("dialog")).not.toBeVisible();
}
async function createOrder(
  user: Page,
  productId: number,
  method: "COD" | "ONLINE",
) {
  await user.goto(`/products/${productId}`);
  await user.getByRole("button", { name: /Thêm vào giỏ/ }).click();
  await user.goto("/checkout");
  await user.getByLabel("Người nhận", { exact: true }).fill("Frontend QA");
  await user.getByLabel("Số điện thoại", { exact: true }).fill("0901234567");
  await user.getByLabel("Địa chỉ cụ thể", { exact: true }).fill("12 Demo QA");
  await user.getByLabel("Phường / xã", { exact: true }).fill("Phường 1");
  await user.getByLabel("Quận / huyện", { exact: true }).fill("Quận 1");
  await user.getByLabel("Tỉnh / thành phố", { exact: true }).fill("TP HCM");
  if (method === "COD")
    await user.getByRole("radio", { name: /Thanh toán khi nhận hàng/ }).check();
  await user.getByRole("button", { name: "Xác nhận đặt hàng" }).click();
  await expect(user).toHaveURL(/\/orders\/\d+$/);
  return Number(new URL(user.url()).pathname.split("/").pop());
}
async function adminToken(page: Page): Promise<string> {
  return page.evaluate(
    () =>
      JSON.parse(sessionStorage.getItem("md05.session") || "{}").accessToken,
  );
}
test("real API: catalog stock, COD delivery collection full return, ONLINE failure and actual 200-second expiry", async ({
  browser,
}) => {
  const result: any = {
    checkedAt: new Date().toISOString(),
    scope:
      "Browser UI through Vite proxy to deployed API; isolated fixture data",
    steps: [],
  };
  const context = await browser.newContext();
  const admin = await context.newPage();
  const user = admin;
  const output = resolve("../.verification/frontend-live/result.json");
  const record = (step: string) => {
    result.steps.push(step);
    writeFileSync(output, JSON.stringify(result, null, 2));
    console.log("LIVE: " + step);
  };
  try {
    await login(admin, credentials.adminEmail, credentials.adminPassword);
    await admin.goto("/admin/products");
    await admin.getByRole("button", { name: "Thêm sản phẩm" }).click();
    await admin
      .getByLabel("Tên sản phẩm", { exact: true })
      .fill(`MONO QA ${credentials.runId}`);
    await admin.getByLabel("Danh mục", { exact: true }).fill("Frontend QA");
    await admin.getByLabel("Thương hiệu", { exact: true }).fill("MONO");
    const created = admin.waitForResponse(
      (r) =>
        new URL(r.url()).pathname === "/api/products" &&
        r.request().method() === "POST",
    );
    await admin
      .getByRole("button", { name: "Lưu sản phẩm", exact: true })
      .click();
    const createdResponse = await created;
    expect(createdResponse.status()).toBe(201);
    const productId = (await createdResponse.json()).data.id;
    result.productId = productId;
    await expect(admin.locator("dialog")).not.toBeVisible();
    await admin.goto("/admin/inventory");
    await admin
      .getByLabel("Tìm sản phẩm trong kho")
      .fill(`MONO QA ${credentials.runId}`);
    await admin
      .getByRole("row")
      .filter({ hasText: `MONO QA ${credentials.runId}` })
      .getByRole("button", { name: "Nhập kho", exact: true })
      .click();
    await admin.getByLabel("Số lượng nhập").fill("20");
    await admin.getByRole("button", { name: "Xác nhận nhập kho" }).click();
    await expect(admin.locator("dialog")).not.toBeVisible();
    record("Admin UI product creation and stock receipt 20");
    await user.evaluate(() => sessionStorage.removeItem("md05.session"));
    await user.goto("/register");
    await user.getByLabel("Họ và tên").fill("Frontend QA");
    await user.getByLabel("Email", { exact: true }).fill(credentials.userEmail);
    await user
      .getByLabel("Mật khẩu", { exact: true })
      .fill(credentials.userPassword);
    await user
      .getByRole("button", { name: "Tạo tài khoản", exact: true })
      .click();
    await expect(user).toHaveURL(/\/login/);
    await login(user, credentials.userEmail, credentials.userPassword);
    const codOrderId = await createOrder(user, productId, "COD");
    result.codOrderId = codOrderId;
    await login(admin, credentials.adminEmail, credentials.adminPassword);
    await admin.goto(`/admin/orders/${codOrderId}`);
    await admin.getByRole("button", { name: "Giao hàng", exact: true }).click();
    await admin
      .getByLabel("Đơn vị vận chuyển", { exact: true })
      .fill("MONO QA");
    await admin
      .getByLabel("Mã vận đơn", { exact: true })
      .fill(`QA-${codOrderId}`);
    await confirm(admin);
    await expect(
      admin.getByRole("button", { name: "Hoàn tất đơn", exact: true }),
    ).toHaveCount(0);
    await admin.waitForTimeout(2500);
    await admin.getByRole("button", { name: "Ghi nhận thu COD" }).click();
    await confirm(admin);
    await admin
      .getByRole("button", { name: "Hoàn tất đơn", exact: true })
      .click();
    await confirm(admin);
    await expect(admin.locator(".page-title .badge")).toHaveText("Hoàn tất");
    record(
      "COD: CONFIRMED -> SHIPPING -> collect acknowledgement -> COMPLETED",
    );
    await login(user, credentials.userEmail, credentials.userPassword);
    await user.goto(`/orders/${codOrderId}`);
    await user
      .getByRole("button", { name: "Yêu cầu trả hàng", exact: true })
      .click();
    await user
      .getByLabel("Lý do trả toàn bộ đơn")
      .fill("Frontend QA: trả toàn bộ hàng thử nghiệm");
    await confirm(user);
    await login(admin, credentials.adminEmail, credentials.adminPassword);
    await admin.goto(`/admin/orders/${codOrderId}`);
    await admin
      .getByRole("button", { name: "Duyệt trả hàng", exact: true })
      .click();
    await admin
      .getByLabel("Ghi chú quyết định")
      .fill("Duyệt dữ liệu thử nghiệm frontend");
    await confirm(admin);
    await admin
      .getByRole("button", { name: "Nhận hàng trả", exact: true })
      .click();
    await confirm(admin);
    await admin.getByRole("button", { name: "Ghi nhận chi hoàn COD" }).click();
    await confirm(admin);
    await expect(
      admin.locator(".badge").filter({ hasText: "Đã hoàn tiền" }).first(),
    ).toBeVisible();
    record(
      "Full COD return: REQUESTED -> APPROVED -> receive/restock -> REFUND_PENDING -> REFUNDED",
    );
    const authorization = {
      Authorization: `Bearer ${await adminToken(admin)}`,
    };
    const readStock = async () =>
      (
        await (
          await admin.request.get(`/api/inventory/${productId}`, {
            headers: authorization,
          })
        ).json()
      ).data;
    expect(await readStock()).toMatchObject({ available: 20, reserved: 0 });
    await login(user, credentials.userEmail, credentials.userPassword);
    const failedOrderId = await createOrder(user, productId, "ONLINE");
    result.failedOrderId = failedOrderId;
    await login(admin, credentials.adminEmail, credentials.adminPassword);
    await admin.goto(`/admin/orders/${failedOrderId}`);
    await admin
      .getByRole("button", { name: "Công cụ kiểm thử sandbox" })
      .click();
    await admin
      .getByRole("button", { name: "Simulate FAILED", exact: true })
      .click();
    await confirm(admin);
    await expect
      .poll(
        async () => {
          await admin
            .getByRole("button", { name: "Cập nhật", exact: true })
            .click();
          return admin.locator(".page-title .badge").innerText();
        },
        { timeout: 20000 },
      )
      .toBe("Đã hủy");
    expect(await readStock()).toMatchObject({ available: 20, reserved: 0 });
    record("ONLINE simulated FAILED -> CANCELLED, stock released");
    await login(user, credentials.userEmail, credentials.userPassword);
    const expiredOrderId = await createOrder(user, productId, "ONLINE");
    result.expiredOrderId = expiredOrderId;
    expect(await readStock()).toMatchObject({ available: 19, reserved: 1 });
    await expect(user.locator(".page-title .badge")).toHaveText("Hết hạn", {
      timeout: 230000,
    });
    expect(await readStock()).toMatchObject({ available: 20, reserved: 0 });
    record(
      "ONLINE actual 200-second wait -> EXPIRED, available 20 / reserved 0",
    );
    result.finalStock = await readStock();
    result.success = true;
  } catch (error) {
    result.success = false;
    result.failure =
      error instanceof Error
        ? error.message.split("\n")[0]
        : "Live test failed";
    throw error;
  } finally {
    result.finishedAt = new Date().toISOString();
    writeFileSync(output, JSON.stringify(result, null, 2));
    await context.close();
  }
});
