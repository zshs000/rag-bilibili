import axios from "axios";

import { apiBaseUrl } from "../config/env";
import { ERROR_CODES } from "../constants/error-codes";
import { normalizeError } from "../utils/error";
import { isDeveloperModeEnabled } from "../utils/dev-mode";
import { logger, sanitizeForLog } from "../utils/logger";

const TOKEN_KEY = "rag_token";

export function saveToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY);
}

function handleNotLoggedIn(normalized) {
  const currentPath = window.location.pathname;
  if (
    !isDeveloperModeEnabled() &&
    normalized.code === ERROR_CODES.NOT_LOGGED_IN &&
    currentPath !== "/login" &&
    currentPath !== "/register"
  ) {
    removeToken();
    const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}`);
    window.location.assign(`/login?redirect=${redirect}`);
  }
}

export const http = axios.create({
  baseURL: apiBaseUrl || "/",
  timeout: 20000,
});

http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  logger.info("http", `请求 ${String(config.method || "GET").toUpperCase()} ${config.url}`, {
    params: sanitizeForLog(config.params),
    data: sanitizeForLog(config.data),
  });
  return config;
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data;
    logger.info("http", `响应 ${response.config.url}`, {
      code: payload?.code,
      message: payload?.message,
    });

    if (payload?.code !== 200) {
      const normalized = normalizeError({
        response: {
          data: payload,
          status: response.status,
        },
      });
      handleNotLoggedIn(normalized);
      return Promise.reject(normalized);
    }

    return payload.data;
  },
  async (error) => {
    const normalized = normalizeError(error);
    logger.error("http", normalized.message, sanitizeForLog(normalized.detail));

    handleNotLoggedIn(normalized);

    return Promise.reject(normalized);
  }
);
