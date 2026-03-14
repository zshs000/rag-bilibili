<template>
  <div class="auth-page">
    <button class="theme-toggle-fab" @click="toggleTheme" :title="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'">
      <el-icon v-if="theme === 'dark'"><Sunny /></el-icon>
      <el-icon v-else><Moon /></el-icon>
    </button>

    <div class="auth-background">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
    </div>

    <div class="auth-container">
      <div class="auth-card">
        <div class="auth-header">
          <RouterLink to="/" class="auth-logo">
            <span class="logo-icon">RB</span>
            <span class="logo-text">RAG Bilibili</span>
          </RouterLink>
          <h1 class="auth-title">登录工作台</h1>
          <p class="auth-description">开始管理你的视频知识库</p>
        </div>

        <el-alert v-if="inlineError" class="auth-alert" type="error" :title="inlineError" show-icon />

        <el-form :model="form" class="auth-form" @submit.prevent="handleSubmit">
          <el-form-item label="用户名">
            <el-input
              v-model.trim="form.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item label="密码">
            <el-input
              v-model.trim="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
              size="large"
              :prefix-icon="Lock"
            />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="auth-submit"
            :loading="authStore.loading"
            @click="handleSubmit"
          >
            立即登录
          </el-button>
        </el-form>

        <div class="auth-footer">
          <span class="auth-footer-text">还没有账号？</span>
          <RouterLink to="/register" class="auth-footer-link">立即注册</RouterLink>
        </div>

        <div v-if="authStore.canUseDeveloperEntry" class="auth-dev">
          <el-divider>开发模式</el-divider>
          <el-button
            type="info"
            size="small"
            plain
            :loading="authStore.loading"
            @click="handleDeveloperEntry"
          >
            进入开发模式
          </el-button>
        </div>
      </div>

      <div class="auth-features">
        <div class="feature-item">
          <div class="feature-icon">✨</div>
          <div class="feature-text">
            <strong>智能问答</strong>
            <span>AI驱动的视频内容检索</span>
          </div>
        </div>
        <div class="feature-item">
          <div class="feature-icon">📚</div>
          <div class="feature-text">
            <strong>知识管理</strong>
            <span>构建个人视频知识库</span>
          </div>
        </div>
        <div class="feature-item">
          <div class="feature-icon">🚀</div>
          <div class="feature-text">
            <strong>快速上手</strong>
            <span>3步开始使用</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from "element-plus";
import { User, Lock, Sunny, Moon } from "@element-plus/icons-vue";
import { reactive, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

import { useAuthStore } from "../stores/auth";
import { useTheme } from "../composables/useTheme";
import { notifyError } from "../utils/error";

const { theme, toggleTheme } = useTheme();

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const inlineError = ref("");
const form = reactive({
  username: "",
  password: "",
});

async function handleSubmit() {
  inlineError.value = "";

  if (!form.username || !form.password) {
    inlineError.value = "用户名和密码不能为空。";
    return;
  }

  try {
    await authStore.login(form);
    ElMessage.success("登录成功");
    await router.push(String(route.query.redirect || "/videos"));
  } catch (error) {
    inlineError.value = notifyError(error).message;
  }
}

async function handleDeveloperEntry() {
  inlineError.value = "";
  try {
    await authStore.enterDeveloperMode();
    ElMessage.success("已进入开发模式");
    await router.push(String(route.query.redirect || "/videos"));
  } catch (error) {
    inlineError.value = notifyError(error).message;
  }
}
</script>

<style scoped>
.theme-toggle-fab {
  position: fixed;
  top: 2rem;
  right: 2rem;
  z-index: 1000;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  color: #ffffff;
  font-size: 1.25rem;
  cursor: pointer;
  transition: all 0.3s ease;
}

.theme-toggle-fab:hover {
  background: rgba(255, 107, 53, 0.2);
  border-color: rgba(255, 107, 53, 0.5);
  transform: scale(1.1);
}

.auth-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #0f1419 0%, #1a1f2e 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  position: relative;
  overflow: hidden;
  transition: background 0.3s ease;
}

:root[data-theme="light"] .auth-page {
  background: linear-gradient(180deg, #fffbf8 0%, #fff5f0 38%, #ffe8dc 100%);
}

.auth-background {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.3;
  animation: float 20s ease-in-out infinite;
}

.orb-1 {
  width: 500px;
  height: 500px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  top: -150px;
  right: -150px;
}

.orb-2 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, #4a90e2 0%, #357abd 100%);
  bottom: -100px;
  left: -100px;
  animation-delay: 5s;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(30px, -30px) scale(1.1);
  }
}

.auth-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 1000px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3rem;
  align-items: center;
}

.auth-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  padding: 3rem;
  transition: all 0.3s ease;
}

:root[data-theme="light"] .auth-card {
  background: rgba(255, 255, 255, 0.9);
  border-color: rgba(255, 107, 53, 0.14);
}

.auth-header {
  text-align: center;
  margin-bottom: 2rem;
}

.auth-logo {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  text-decoration: none;
  margin-bottom: 2rem;
}

.logo-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  border-radius: 12px;
  font-weight: 700;
  font-size: 1.5rem;
  color: #ffffff;
}

.logo-text {
  font-size: 1.5rem;
  font-weight: 700;
  color: #ffffff;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .logo-text {
  color: #1a1f2e;
}

.auth-title {
  font-size: 2rem;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 0.5rem;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .auth-title {
  color: #1a1f2e;
}

.auth-description {
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.7);
  transition: color 0.3s ease;
}

:root[data-theme="light"] .auth-description {
  color: #53657f;
}

.auth-alert {
  margin-bottom: 1.5rem;
}

.auth-form {
  margin-bottom: 1.5rem;
}

.auth-form :deep(.el-form-item__label) {
  color: rgba(255, 255, 255, 0.9);
  font-weight: 500;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .auth-form :deep(.el-form-item__label) {
  color: #1a1f2e;
}

.auth-form :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: none;
  transition: all 0.3s ease;
}

:root[data-theme="light"] .auth-form :deep(.el-input__wrapper) {
  background: #ffffff;
  border-color: rgba(255, 107, 53, 0.14);
}

.auth-form :deep(.el-input__wrapper:hover) {
  border-color: rgba(255, 107, 53, 0.5);
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  border-color: #ff6b35;
  box-shadow: 0 0 0 2px rgba(255, 107, 53, 0.2);
}

.auth-form :deep(.el-input__inner) {
  color: #ffffff;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .auth-form :deep(.el-input__inner) {
  color: #1a1f2e;
}

.auth-form :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.4);
  transition: color 0.3s ease;
}

:root[data-theme="light"] .auth-form :deep(.el-input__inner::placeholder) {
  color: rgba(26, 31, 46, 0.4);
}

.auth-form :deep(.el-input__prefix) {
  color: rgba(255, 255, 255, 0.5);
  transition: color 0.3s ease;
}

:root[data-theme="light"] .auth-form :deep(.el-input__prefix) {
  color: rgba(26, 31, 46, 0.5);
}

.auth-submit {
  width: 100%;
  height: 48px;
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  border: none;
  font-size: 1rem;
  font-weight: 600;
}

.auth-submit:hover {
  background: linear-gradient(135deg, #ff7a45 0%, #ffa02e 100%);
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(255, 107, 53, 0.4);
}

.auth-footer {
  text-align: center;
  padding-top: 1.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  transition: border-color 0.3s ease;
}

:root[data-theme="light"] .auth-footer {
  border-top-color: rgba(255, 107, 53, 0.14);
}

.auth-footer-text {
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.875rem;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .auth-footer-text {
  color: #53657f;
}

.auth-footer-link {
  color: #ff6b35;
  text-decoration: none;
  font-weight: 600;
  margin-left: 0.5rem;
}

.auth-footer-link:hover {
  text-decoration: underline;
}

.auth-dev {
  margin-top: 2rem;
  text-align: center;
}

.auth-dev :deep(.el-divider__text) {
  background: transparent;
  color: rgba(255, 255, 255, 0.5);
  font-size: 0.75rem;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .auth-dev :deep(.el-divider__text) {
  color: rgba(26, 31, 46, 0.5);
}

.auth-features {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  transition: all 0.3s ease;
}

:root[data-theme="light"] .feature-item {
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(255, 107, 53, 0.14);
}

.feature-icon {
  font-size: 2rem;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(255, 107, 53, 0.2) 0%, rgba(74, 144, 226, 0.2) 100%);
  border-radius: 12px;
}

.feature-text {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.feature-text strong {
  color: #ffffff;
  font-size: 1.125rem;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .feature-text strong {
  color: #1a1f2e;
}

.feature-text span {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.875rem;
  transition: color 0.3s ease;
}

:root[data-theme="light"] .feature-text span {
  color: #53657f;
}

@media (max-width: 768px) {
  .auth-container {
    grid-template-columns: 1fr;
  }

  .auth-features {
    display: none;
  }

  .auth-card {
    padding: 2rem;
  }
}
</style>
