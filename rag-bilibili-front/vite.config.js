import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxyTarget = env.VITE_PROXY_TARGET || "http://localhost:8080";

  return {
    plugins: [
      vue(),
      AutoImport({
        imports: ["vue", "vue-router"],
        resolvers: [ElementPlusResolver({ directives: true })],
      }),
      Components({
        resolvers: [ElementPlusResolver({ directives: true })],
      }),
    ],
    server: {
      host: "0.0.0.0",
      port: 5173,
      proxy: {
        "/api": {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes("node_modules/vue") || id.includes("node_modules/vue-router") || id.includes("node_modules/pinia")) {
              return "vue-core";
            }
            if (id.includes("node_modules/element-plus") || id.includes("node_modules/@element-plus")) {
              return undefined;
            }
            if (id.includes("node_modules")) {
              return "vendor";
            }
            return null;
          },
        },
      },
    },
  };
});
