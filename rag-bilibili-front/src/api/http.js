import axios from "axios";

import { apiBaseUrl } from "../config/env";
import { ERROR_CODES } from "../constants/error-codes";
import { normalizeError } from "../utils/error";
import { isDeveloperModeEnabled } from "../utils/dev-mode";
import { logger, sanitizeForLog } from "../utils/logger";

export const http = axios.create({
  baseURL: apiBaseUrl || "/",
  withCredentials: true,
  timeout: 20000,
});

http.interceptors.request.use((config) => {
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
      return Promise.reject(
        normalizeError({
          response: {
            data: payload,
            status: response.status,
          },
        })
      );
    }

    return payload.data;
  },
  async (error) => {
    const normalized = normalizeError(error);
    logger.error("http", normalized.message, sanitizeForLog(normalized.detail));

    const currentPath = window.location.pathname;
    if (
      !isDeveloperModeEnabled() &&
      normalized.code === ERROR_CODES.NOT_LOGGED_IN &&
      currentPath !== "/login" &&
      currentPath !== "/register"
    ) {
      const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}`);
      window.location.assign(`/login?redirect=${redirect}`);
    }

    return Promise.reject(normalized);
  }
);
