import { ElMessage } from "element-plus";

import { ERROR_CODES, ERROR_MESSAGES } from "../constants/error-codes";

export function normalizeError(error) {
  if (error?.normalized) {
    return error;
  }

  const payload = error?.response?.data;
  const code = payload?.code ?? error?.response?.status ?? ERROR_CODES.SYSTEM_ERROR;

  return {
    normalized: true,
    code,
    message: payload?.message || ERROR_MESSAGES[code] || error?.message || "系统错误，请稍后重试。",
    detail: payload || error,
  };
}

export function notifyError(error, customMessage) {
  const normalized = normalizeError(error);
  ElMessage.error(customMessage || normalized.message);
  return normalized;
}
