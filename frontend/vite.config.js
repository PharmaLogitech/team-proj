/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Vite configuration file.                                             ║
 * ║                                                                              ║
 * ║  WHY:  Vite is the build tool and dev server for our React frontend.        ║
 * ║        This file tells Vite:                                                ║
 * ║          1. Use the React plugin (for JSX transformation & fast refresh).   ║
 * ║          2. Proxy /api requests to the Spring Boot backend at :8080.        ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add aliases: resolve: { alias: { '@': '/src' } }                  ║
 * ║        - Add environment-specific configs with Vite's mode system.          ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],

  server: {
    /*
     * The proxy lets the frontend call fetch("/api/products") without
     * specifying the full backend URL (http://localhost:8080).
     *
     * When Vite sees a request starting with "/api", it forwards it to
     * the Spring Boot backend.  This avoids CORS issues during development
     * and makes the code cleaner (no hardcoded URLs).
     *
     * In production, you'd configure your reverse proxy (Nginx, etc.)
     * to do the same thing.
     */
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
