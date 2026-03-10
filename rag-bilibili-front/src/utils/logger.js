const SENSITIVE_KEYS = new Set([
  "password",
  "sessdata",
  "biliJct",
  "bili_jct",
  "buvid3",
  "authorization",
  "cookie",
]);

function sanitizeValue(value) {
  if (Array.isArray(value)) {
    return value.map((item) => sanitizeValue(item));
  }

  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, innerValue]) => {
        if (SENSITIVE_KEYS.has(key)) {
          return [key, "***"];
        }
        return [key, sanitizeValue(innerValue)];
      })
    );
  }

  return value;
}

export function sanitizeForLog(value) {
  return sanitizeValue(value);
}

function write(level, scope, message, detail) {
  const printer = level === "error" ? console.error : level === "warn" ? console.warn : console.log;
  printer(`[${scope}] ${message}`, detail ? sanitizeForLog(detail) : "");
}

export const logger = {
  info(scope, message, detail) {
    write("info", scope, message, detail);
  },
  warn(scope, message, detail) {
    write("warn", scope, message, detail);
  },
  error(scope, message, detail) {
    write("error", scope, message, detail);
  },
};
