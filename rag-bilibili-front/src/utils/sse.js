import { logger } from "./logger";

function decodeSseEvent(rawEvent) {
  const lines = rawEvent.split("\n");
  let explicitEvent = "message";
  const dataLines = [];

  lines.forEach((line) => {
    if (line.startsWith("event:")) {
      explicitEvent = line.slice(6).trim();
    }
    if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trim());
    }
  });

  if (!dataLines.length) {
    return null;
  }

  const dataText = dataLines.join("\n");
  try {
    const payload = JSON.parse(dataText);
    return {
      event: payload.type || explicitEvent,
      payload,
    };
  } catch (error) {
    logger.error("sse", "SSE 数据解析失败", {
      rawEvent,
      error,
    });
    return {
      event: explicitEvent,
      payload: {
        type: explicitEvent,
        raw: dataText,
      },
    };
  }
}

export async function consumeSseStream(response, handlers = {}) {
  if (!response.ok) {
    throw new Error(`SSE 请求失败: ${response.status}`);
  }

  if (!response.body) {
    throw new Error("当前浏览器不支持流式响应读取。");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split("\n\n");
    buffer = events.pop() || "";

    for (const rawEvent of events) {
      const parsed = decodeSseEvent(rawEvent.trim());
      if (!parsed) {
        continue;
      }

      const handler = handlers[parsed.event] || handlers.message;
      if (handler) {
        await handler(parsed.payload);
      }
    }
  }

  if (buffer.trim()) {
    const parsed = decodeSseEvent(buffer.trim());
    if (parsed) {
      const handler = handlers[parsed.event] || handlers.message;
      if (handler) {
        await handler(parsed.payload);
      }
    }
  }
}
