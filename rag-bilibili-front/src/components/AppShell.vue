<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="nav-brand">
        <div class="nav-mark">RB</div>
        <div>
          <strong>RAG-Bilibili</strong>
          <div class="muted">Video Knowledge Workspace</div>
        </div>
      </div>

      <div class="surface-strong card-section">
        <div class="eyebrow">Current User</div>
        <div class="top-gap">
          <strong>{{ authStore.user?.username || "未登录" }}</strong>
          <div class="muted top-gap">
            {{ authStore.isDeveloperMode ? "开发测试模式已启用" : "你的内容仅在当前账号下管理" }}
          </div>
        </div>
      </div>

      <nav class="nav-links">
        <RouterLink v-for="item in navItems" :key="item.name" class="nav-link" :to="{ name: item.name }">
          <span>{{ item.label }}</span>
          <el-icon><component :is="item.icon" /></el-icon>
        </RouterLink>
      </nav>

      <div class="nav-footer surface-strong card-section">
        <div class="toolbar">
          <el-button type="primary" plain @click="handleLogout">退出登录</el-button>
        </div>
      </div>
    </aside>

    <main class="app-main">
      <header class="page-header surface">
        <div>
          <div class="eyebrow">{{ eyebrow }}</div>
          <h1 class="page-title">{{ title }}</h1>
          <p class="page-subtitle">{{ subtitle }}</p>
        </div>
        <div>
          <slot name="header-actions" />
        </div>
      </header>

      <slot />
    </main>
  </div>
</template>

<script setup>
import { ChatDotRound, Collection, Download } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { RouterLink, useRouter } from "vue-router";

import { useAuthStore } from "../stores/auth";
import { notifyError } from "../utils/error";

defineProps({
  eyebrow: {
    type: String,
    default: "Workspace",
  },
  title: {
    type: String,
    required: true,
  },
  subtitle: {
    type: String,
    required: true,
  },
});

const router = useRouter();
const authStore = useAuthStore();

const navItems = [
  { name: "import", label: "导入视频", icon: Download },
  { name: "videos", label: "视频列表", icon: Collection },
  { name: "sessions", label: "会话列表", icon: ChatDotRound },
];

async function handleLogout() {
  try {
    await authStore.logout();
    ElMessage.success("已退出登录");
    await router.push({ name: "login" });
  } catch (error) {
    notifyError(error);
  }
}
</script>
