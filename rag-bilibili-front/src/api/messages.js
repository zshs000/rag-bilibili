import { getToken, handleNotLoggedIn, http } from "./http";
import { devServer } from "../mock/dev-server";
import { resolveApiPath } from "../config/env";
import { isDeveloperModeEnabled } from "../utils/dev-mode";
import { consumeSseStream } from "../utils/sse";
import { normalizeError } from "../utils/error";
import { logger } from "../utils/logger";

export const messagesApi = {
  list(sessionId) {
    if (isDeveloperModeEnabled()) {
      return devServer.listMessages(sessionId);
    }
    return http.get(`/sessions/${sessionId}/messages`);
  },
  async stream(sessionId, payload, handlers, signal) {
    if (isDeveloperModeEnabled()) {
      return devServer.streamMessage(sessionId, payload, handlers, signal);
    }
    const url = resolveApiPath(`/sessions/${sessionId}/messages/stream`);
    const token = getToken();
    logger.info("chat", `开始流式请求 ${url}`);

    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(payload),
      signal,
    });

    const contentType = response.headers.get("content-type") || "";
    if (!response.ok || !contentType.toLowerCase().startsWith("text/event-stream")) {
      let errorPayload = null;
      try {
        errorPayload = await response.json();
      } catch {
        // ignore parse error
      }

      const normalized = normalizeError({
        response: {
          status: response.status,
          data: errorPayload,
        },
      });
      handleNotLoggedIn(normalized);
      throw normalized;
    }

    return consumeSseStream(response, handlers);
  },
};
