import path from "node:path"
import { fileURLToPath } from "node:url"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

const root = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "#components": path.resolve(root, "src/components"),
      "#hooks": path.resolve(root, "src/hooks"),
      "#lib": path.resolve(root, "src/lib"),
    },
  },
  build: {
    lib: {
      entry: path.resolve(root, "src/index.tsx"),
      formats: ["es"],
      fileName: "index",
    },
    rollupOptions: {
      external: ["react", "react-dom", "react/jsx-runtime"],
    },
  },
})
