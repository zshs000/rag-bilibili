import { computed, ref } from "vue";
import { defineStore } from "pinia";

import { authApi } from "../api/auth";
import {
  DEV_ENTRY_PASSWORD,
  DEV_ENTRY_USERNAME,
  disableDeveloperMode,
  enableDeveloperMode,
  getDevUserProfile,
  isDeveloperEntryAvailable,
  isDeveloperModeEnabled,
} from "../utils/dev-mode";
import { logger } from "../utils/logger";

export const useAuthStore = defineStore("auth", () => {
  const user = ref(null);
  const initialized = ref(false);
  const loading = ref(false);

  const isAuthenticated = computed(() => Boolean(user.value?.id));
  const isDeveloperMode = computed(() => isDeveloperModeEnabled());
  const canUseDeveloperEntry = computed(() => isDeveloperEntryAvailable());

  async function fetchCurrentUser() {
    loading.value = true;
    try {
      if (isDeveloperModeEnabled()) {
        user.value = getDevUserProfile();
        logger.info("auth", "恢复开发模式用户", user.value);
      } else {
        user.value = await authApi.current();
        logger.info("auth", "获取当前用户成功", user.value);
      }
    } finally {
      initialized.value = true;
      loading.value = false;
    }

    return user.value;
  }

  async function register(payload) {
    loading.value = true;
    try {
      const result = await authApi.register(payload);
      logger.info("auth", "用户注册成功", result);
      return result;
    } finally {
      loading.value = false;
    }
  }

  async function login(payload) {
    loading.value = true;
    try {
      disableDeveloperMode();
      user.value = await authApi.login(payload);
      logger.info("auth", "用户登录成功", user.value);
      return user.value;
    } finally {
      initialized.value = true;
      loading.value = false;
    }
  }

  async function enterDeveloperMode(payload = {}) {
    loading.value = true;
    try {
      if (!isDeveloperEntryAvailable()) {
        throw new Error("开发人员入口当前已关闭。");
      }

      const username = payload.username ?? DEV_ENTRY_USERNAME;
      const password = payload.password ?? DEV_ENTRY_PASSWORD;
      if (username !== DEV_ENTRY_USERNAME || password !== DEV_ENTRY_PASSWORD) {
        throw new Error("开发人员入口账号或密码不正确。");
      }

      enableDeveloperMode();
      user.value = getDevUserProfile();
      logger.info("auth", "已进入开发模式", user.value);
      return user.value;
    } finally {
      initialized.value = true;
      loading.value = false;
    }
  }

  async function logout() {
    loading.value = true;
    try {
      if (isDeveloperModeEnabled()) {
        logger.info("auth", "退出开发模式");
      } else {
        await authApi.logout();
        logger.info("auth", "用户登出成功");
      }
    } finally {
      disableDeveloperMode();
      user.value = null;
      initialized.value = true;
      loading.value = false;
    }
  }

  return {
    user,
    initialized,
    loading,
    isAuthenticated,
    isDeveloperMode,
    canUseDeveloperEntry,
    fetchCurrentUser,
    register,
    login,
    enterDeveloperMode,
    logout,
  };
});
