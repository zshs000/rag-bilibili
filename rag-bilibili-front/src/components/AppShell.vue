<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <RouterLink to="/" class="nav-brand">
        <div class="nav-mark">RB</div>
        <div class="nav-brand-text">
          <strong>RAG Bilibili</strong>
          <div class="nav-brand-subtitle">Video Knowledge</div>
        </div>
      </RouterLink>

      <div class="user-card">
        <div class="user-avatar">
          {{ (authStore.user?.username || "U")[0].toUpperCase() }}
        </div>
        <div class="user-info">
          <strong class="user-name">{{ authStore.user?.username || "未登录" }}</strong>
          <span class="user-status">
            {{ authStore.isDeveloperMode ? "开发模式" : "已登录" }}
          </span>
        </div>
      </div>

      <nav class="nav-links">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          class="nav-link"
          :to="{ name: item.name }"
        >
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span class="nav-label">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="nav-footer">
        <button class="theme-toggle-button" @click="toggleTheme">
          <el-icon v-if="theme === 'dark'"><Sunny /></el-icon>
          <el-icon v-else><Moon /></el-icon>
          <span>{{ theme === 'dark' ? '浅色模式' : '深色模式' }}</span>
        </button>
        <button class="logout-button" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <main class="app-main">
      <div class="main-background">
        <div class="gradient-orb orb-1"></div>
        <div class="gradient-orb orb-2"></div>
      </div>

      <header class="page-header">
        <div class="header-content">
          <div class="header-text">
            <div class="header-eyebrow">{{ eyebrow }}</div>
            <h1 class="header-title">{{ title }}</h1>
            <p class="header-subtitle">{{ subtitle }}</p>
          </div>
          <div class="header-actions">
            <slot name="header-actions" />
          </div>
        </div>
      </header>

      <div class="page-content">
        <slot />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ChatDotRound, Collection, Download, SwitchButton, Sunny, Moon } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { RouterLink, useRouter } from "vue-router";

import { useAuthStore } from "../stores/auth";
import { useTheme } from "../composables/useTheme";
import { notifyError } from "../utils/error";

const { theme, toggleTheme } = useTheme();

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

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: #0f1419;
}

:root[data-theme="light"] .app-shell {
  background: #ffffff;
}

/* Sidebar */
.app-sidebar {
  width: 280px;
  background: rgba(26, 31, 46, 0.8);
  backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  transition: all 0.3s ease;
}

:root[data-theme="light"] .app-sidebar {
  background: rgba(255, 255, 255, 0.9);
  border-right-color: rgba(255, 107, 53, 0.14);
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 2rem;
  text-decoration: none;
  padding: 0.75rem;
  border-radius: 12px;
  transition: background 0.3s ease;
}

.nav-brand:hover {
  background: rgba(255, 255, 255, 0.05);
}

.nav-mark {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  border-radius: 12px;
  font-weight: 700;
  font-size: 1.25rem;
  color: #ffffff;
  flex-shrink: 0;
}

.nav-brand-text {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.nav-brand-text strong {
  color: #ffffff;
  font-size: 1.125rem;
  font-weight: 600;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .nav-brand-text strong {
  color: #1a1f2e;
}

.nav-brand-subtitle {
  color: rgba(255, 255, 255, 0.5);
  font-size: 0.75rem;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .nav-brand-subtitle {
  color: #53657f;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  margin-bottom: 1.5rem;
  transition: all 0.3s ease;
}

:root[data-theme="light"] .user-card {
  background: rgba(255, 107, 53, 0.08);
  border-color: rgba(255, 107, 53, 0.14);
}

.user-avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #4a90e2 0%, #357abd 100%);
  border-radius: 50%;
  font-weight: 600;
  color: #ffffff;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.user-name {
  color: #ffffff;
  font-size: 0.875rem;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .user-name {
  color: #1a1f2e;
}

.user-status {
  color: rgba(255, 255, 255, 0.5);
  font-size: 0.75rem;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .user-status {
  color: #53657f;
}

.nav-links {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  border-radius: 8px;
  transition: all 0.3s ease;
  font-size: 0.9375rem;
}

:root[data-theme="light"] .nav-link {
  color: #53657f;
}

.nav-link:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #ffffff;
}

:root[data-theme="light"] .nav-link:hover {
  background: rgba(255, 107, 53, 0.08);
  color: #1a1f2e;
}

.nav-link.router-link-active {
  background: linear-gradient(135deg, rgba(255, 107, 53, 0.15) 0%, rgba(247, 147, 30, 0.15) 100%);
  border: 1px solid rgba(255, 107, 53, 0.3);
  color: #ff6b35;
}

:root[data-theme="light"] .nav-link.router-link-active {
  background: linear-gradient(135deg, rgba(255, 107, 53, 0.12) 0%, rgba(247, 147, 30, 0.12) 100%);
  border-color: rgba(255, 107, 53, 0.3);
}

.nav-icon {
  font-size: 1.25rem;
}

.nav-label {
  font-weight: 500;
}

.nav-footer {
  margin-top: auto;
  padding-top: 1rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  transition: border-color 0.3s ease;
}

:root[data-theme="light"] .nav-footer {
  border-top-color: rgba(255, 107, 53, 0.14);
}

.theme-toggle-button {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.875rem;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.9375rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

:root[data-theme="light"] .theme-toggle-button {
  background: rgba(255, 107, 53, 0.05);
  border-color: rgba(255, 107, 53, 0.14);
  color: #53657f;
}

.theme-toggle-button:hover {
  background: rgba(255, 107, 53, 0.1);
  border-color: rgba(255, 107, 53, 0.3);
  color: #ff6b35;
}

:root[data-theme="light"] .theme-toggle-button:hover {
  background: rgba(255, 107, 53, 0.12);
  color: #ff6b35;
}

.logout-button {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.875rem;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.9375rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

:root[data-theme="light"] .logout-button {
  background: rgba(255, 107, 53, 0.05);
  border-color: rgba(255, 107, 53, 0.14);
  color: #53657f;
}

.logout-button:hover {
  background: rgba(255, 107, 53, 0.1);
  border-color: rgba(255, 107, 53, 0.3);
  color: #ff6b35;
}

:root[data-theme="light"] .logout-button:hover {
  background: rgba(255, 107, 53, 0.12);
  color: #ff6b35;
}

/* Main Content */
.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.main-background {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.15;
  animation: float 25s ease-in-out infinite;
  transition: opacity 0.3s ease;
}

:root[data-theme="light"] .gradient-orb {
  opacity: 0.08;
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  top: -200px;
  right: -200px;
}

.orb-2 {
  width: 500px;
  height: 500px;
  background: linear-gradient(135deg, #4a90e2 0%, #357abd 100%);
  bottom: -150px;
  left: 50%;
  animation-delay: 10s;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  33% {
    transform: translate(50px, -50px) scale(1.1);
  }
  66% {
    transform: translate(-30px, 30px) scale(0.9);
  }
}

.page-header {
  position: relative;
  z-index: 1;
  padding: 2rem 3rem;
  background: rgba(26, 31, 46, 0.6);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
}

:root[data-theme="light"] .page-header {
  background: rgba(255, 255, 255, 0.7);
  border-bottom-color: rgba(255, 107, 53, 0.14);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 2rem;
}

.header-text {
  flex: 1;
}

.header-eyebrow {
  display: inline-block;
  padding: 0.375rem 0.75rem;
  background: rgba(255, 107, 53, 0.1);
  border: 1px solid rgba(255, 107, 53, 0.3);
  border-radius: 50px;
  font-size: 0.75rem;
  color: #ff6b35;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 1rem;
}

.header-title {
  font-size: 2.5rem;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 0.5rem;
  line-height: 1.2;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .header-title {
  color: #1a1f2e;
}

.header-subtitle {
  font-size: 1.125rem;
  color: rgba(255, 255, 255, 0.7);
  line-height: 1.5;
  max-width: 600px;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .header-subtitle {
  color: #53657f;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.page-content {
  position: relative;
  z-index: 1;
  flex: 1;
  padding: 2rem 3rem;
  overflow-y: auto;
  background: transparent;
  transition: background-color 0.3s ease;
}

:root[data-theme="light"] .page-content {
  background: transparent;
}

/* Element Plus Overrides */
.page-content :deep(.el-button--primary) {
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  border: none;
}

.page-content :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #ff7a45 0%, #ffa02e 100%);
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(255, 107, 53, 0.3);
}

/* Responsive */
@media (max-width: 1024px) {
  .app-sidebar {
    width: 240px;
  }

  .page-header {
    padding: 1.5rem 2rem;
  }

  .page-content {
    padding: 1.5rem 2rem;
  }

  .header-title {
    font-size: 2rem;
  }
}

@media (max-width: 768px) {
  .app-shell {
    flex-direction: column;
  }

  .app-sidebar {
    width: 100%;
    height: auto;
    position: static;
    border-right: none;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  }

  .page-header {
    padding: 1.5rem;
  }

  .page-content {
    padding: 1.5rem;
  }

  .header-content {
    flex-direction: column;
  }

  .header-title {
    font-size: 1.75rem;
  }
}
</style>
