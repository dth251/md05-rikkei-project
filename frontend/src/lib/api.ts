import type { Session } from "../types";

const SESSION_KEY = "md05.session";
let current: Session | null = null;
try {
  current = JSON.parse(sessionStorage.getItem(SESSION_KEY) || "null");
} catch {
  sessionStorage.removeItem(SESSION_KEY);
}
let refreshing: Promise<Session> | null = null;
const listeners = new Set<() => void>();
export function getSession() {
  return current;
}
export function saveSession(session: Session | null) {
  current = session;
  if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  else sessionStorage.removeItem(SESSION_KEY);
  listeners.forEach((listener) => listener());
}
export function subscribeSession(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors?: Record<string, string>,
  ) {
    super(message);
  }
}
async function refreshSession(): Promise<Session> {
  if (!current?.refreshToken)
    throw new ApiError(401, "Phiên đăng nhập đã hết hạn.");
  const response = await fetch("/api/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: current.refreshToken }),
  });
  const body = await response.json();
  if (!response.ok) {
    if ([400, 401, 403, 409].includes(response.status)) saveSession(null);
    throw new ApiError(
      response.status,
      response.status === 429 || response.status === 503
        ? "Chưa thể làm mới phiên. Vui lòng đợi một chút rồi thử lại."
        : "Phiên đã hết hạn. Vui lòng đăng nhập lại.",
    );
  }
  saveSession(body.data);
  return body.data;
}
export async function api<T>(
  path: string,
  options: Omit<RequestInit, "body"> & {
    body?: unknown;
    noAuth?: boolean;
  } = {},
  retry = true,
): Promise<T> {
  const { body, noAuth, ...rest } = options;
  const headers = new Headers(rest.headers);
  if (body !== undefined) headers.set("Content-Type", "application/json");
  if (current && !noAuth)
    headers.set("Authorization", `Bearer ${current.accessToken}`);
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...rest,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(
      0,
      "Không kết nối được hệ thống. Kiểm tra mạng rồi thử lại.",
    );
  }
  if (response.status === 401 && retry && current?.refreshToken && !noAuth) {
    if (!refreshing)
      refreshing = refreshSession().finally(() => {
        refreshing = null;
      });
    await refreshing;
    return api<T>(path, options, false);
  }
  if (response.status === 204) return undefined as T;
  const data = await response.json().catch(() => null);
  if (!response.ok || data?.success === false) {
    const message =
      response.status === 429
        ? "Bạn thao tác quá nhanh. Vui lòng đợi một chút."
        : response.status === 503
          ? "Hệ thống tạm bận. Vui lòng thử lại sau."
          : data?.message || `Yêu cầu không thành công (${response.status}).`;
    throw new ApiError(response.status, message, data?.errors);
  }
  return data?.data as T;
}
export function query(values: Record<string, unknown>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== "" && value !== undefined && value !== null)
      params.set(key, String(value));
  });
  return params.toString();
}
export function readableError(error: unknown) {
  if (
    error instanceof ApiError &&
    error.errors &&
    typeof error.errors === "object"
  )
    return Object.values(error.errors).join(" · ") || error.message;
  return error instanceof Error
    ? error.message
    : "Đã xảy ra lỗi. Vui lòng thử lại.";
}
