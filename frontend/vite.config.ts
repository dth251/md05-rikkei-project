import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const target =
  process.env.API_PROXY_TARGET || "https://md05-rikkei-project.duckdns.org";
const proxy = {
  "/api": { target, changeOrigin: true, secure: true },
  "/gateway-status": { target, changeOrigin: true, secure: true },
};

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true, proxy },
  preview: { port: 4173, strictPort: true, proxy },
  build: { sourcemap: false },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
