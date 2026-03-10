const rawBaseUrl = import.meta.env.VITE_API_BASE_URL || "/api";

function trimSlash(value) {
  return value.replace(/\/+$/, "");
}

export const apiBaseUrl = rawBaseUrl === "/" ? "" : trimSlash(rawBaseUrl);

export function resolveApiPath(path) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  if (!apiBaseUrl) {
    return normalizedPath;
  }
  return `${apiBaseUrl}${normalizedPath}`;
}
