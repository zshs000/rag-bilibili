import { createRouter, createWebHistory } from "vue-router";

import { pinia } from "../stores";
import { ERROR_CODES } from "../constants/error-codes";
import { useAuthStore } from "../stores/auth";
import { logger } from "../utils/logger";

const routes = [
  { path: "/", redirect: "/videos" },
  {
    path: "/login",
    name: "login",
    component: () => import("../views/LoginView.vue"),
    meta: { guestOnly: true, title: "登录" },
  },
  {
    path: "/register",
    name: "register",
    component: () => import("../views/RegisterView.vue"),
    meta: { guestOnly: true, title: "注册" },
  },
  {
    path: "/import",
    name: "import",
    component: () => import("../views/ImportView.vue"),
    meta: { requiresAuth: true, title: "导入视频" },
  },
  {
    path: "/videos",
    name: "videos",
    component: () => import("../views/VideosView.vue"),
    meta: { requiresAuth: true, title: "视频列表" },
  },
  {
    path: "/sessions",
    name: "sessions",
    component: () => import("../views/SessionsView.vue"),
    meta: { requiresAuth: true, title: "会话列表" },
  },
  {
    path: "/chat/:sessionId",
    name: "chat",
    component: () => import("../views/ChatView.vue"),
    meta: { requiresAuth: true, title: "对话" },
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/videos",
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia);

  if (!authStore.initialized) {
    try {
      await authStore.fetchCurrentUser();
    } catch (error) {
      if (error.code !== ERROR_CODES.NOT_LOGGED_IN) {
        logger.error("router", "初始化用户状态失败", error);
      }
    }
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return {
      name: "login",
      query: {
        redirect: to.fullPath,
      },
    };
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return { name: "videos" };
  }

  document.title = `${to.meta.title || "工作台"} | RAG-Bilibili`;
  return true;
});

export default router;
