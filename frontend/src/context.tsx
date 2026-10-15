import {
  createContext,
  useContext,
  useState,
  useSyncExternalStore,
  useCallback,
  type ReactNode,
} from "react";
import type { CartItem, Product, Session } from "./types";
import { api, getSession, saveSession, subscribeSession } from "./lib/api";

export function useAuth() {
  const session = useSyncExternalStore(subscribeSession, getSession);
  return {
    session,
    user: session?.user,
    login: async (email: string, password: string) => {
      const next = await api<Session>("/auth/login", {
        method: "POST",
        body: { email, password },
        noAuth: true,
      });
      saveSession(next);
      return next;
    },
    logout: async () => {
      try {
        if (session)
          await api("/auth/logout", {
            method: "POST",
            body: { refreshToken: session.refreshToken },
          });
      } catch {
        // Local logout remains available when server-side revocation is temporarily unavailable.
      } finally {
        saveSession(null);
      }
    },
  };
}
const CartContext = createContext<{
  items: CartItem[];
  add: (p: Product, n?: number) => void;
  setQuantity: (id: number, n: number) => void;
  clear: () => void;
}>({ items: [], add: () => {}, setQuantity: () => {}, clear: () => {} });
export function CartProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<CartItem[]>(() => {
    try {
      return JSON.parse(localStorage.getItem("md05.cart") || "[]").filter(
        (x: CartItem) => x.product?.id && x.quantity > 0,
      );
    } catch {
      return [];
    }
  });
  const update = (next: CartItem[]) => {
    setItems(next);
    localStorage.setItem("md05.cart", JSON.stringify(next));
  };
  return (
    <CartContext.Provider
      value={{
        items,
        add(product, quantity = 1) {
          const found = items.find((i) => i.product.id === product.id);
          update(
            found
              ? items.map((i) =>
                  i.product.id === product.id
                    ? { ...i, quantity: Math.min(10000, i.quantity + quantity) }
                    : i,
                )
              : [...items, { product, quantity }],
          );
        },
        setQuantity(id, n) {
          update(
            items
              .map((i) => (i.product.id === id ? { ...i, quantity: n } : i))
              .filter((i) => i.quantity > 0),
          );
        },
        clear() {
          update([]);
        },
      }}
    >
      {children}
    </CartContext.Provider>
  );
}
export const useCart = () => useContext(CartContext);
const ToastContext = createContext<(text: string) => void>(() => {});
export function ToastProvider({ children }: { children: ReactNode }) {
  const [text, setText] = useState("");
  const notify = useCallback((message: string) => {
    setText(message);
    window.setTimeout(() => setText(""), 5000);
  }, []);
  return (
    <ToastContext.Provider value={notify}>
      {children}
      {text && (
        <div className="toast" role="status" onClick={() => setText("")}>
          {text}
          <span>×</span>
        </div>
      )}
    </ToastContext.Provider>
  );
}
export const useToast = () => useContext(ToastContext);
