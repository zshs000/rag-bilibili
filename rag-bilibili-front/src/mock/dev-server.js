import { ERROR_CODES } from "../constants/error-codes";

const DB_KEY = "rag-bilibili-dev-db";

function nowString() {
  const now = new Date();
  const pad = (value) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}

function createError(code, message) {
  return {
    normalized: true,
    code,
    message,
    detail: {
      code,
      message,
    },
  };
}

function extractBvid(input) {
  const matched = String(input || "").match(/BV[a-zA-Z0-9]+/);
  return matched ? matched[0] : "";
}

function defaultDatabase() {
  const createTime = nowString();
  return {
    nextVideoId: 4,
    nextImportBatchId: 1,
    nextSessionId: 3,
    nextMessageId: 7,
    videos: [
      {
        id: 1,
        bvid: "BV1DCfsBKExV",
        title: "Spring AI Alibaba 入门与 RAG 基础",
        description: "介绍字幕读取、分片、向量化、过滤条件以及基础问答链路。",
        chunkCount: 48,
        importTime: createTime,
        status: "SUCCESS",
        failReason: null,
      },
      {
        id: 2,
        bvid: "BV1sa4y1K7un",
        title: "DashVector 检索过滤实践",
        description: "围绕 user_id、bvid 过滤检索与删除联动的实现思路。",
        chunkCount: 36,
        importTime: createTime,
        status: "SUCCESS",
        failReason: null,
      },
      {
        id: 3,
        bvid: "BV1qA411M7zk",
        title: "多轮问答与 SSE 流式返回",
        description: "演示 start/content/end 事件驱动的流式聊天体验。",
        chunkCount: 29,
        importTime: createTime,
        status: "SUCCESS",
        failReason: null,
      },
    ],
    importBatches: [],
    sessions: [
      {
        id: 1,
        sessionType: "SINGLE_VIDEO",
        videoId: 1,
        videoTitle: "Spring AI Alibaba 入门与 RAG 基础",
        createTime,
      },
      {
        id: 2,
        sessionType: "ALL_VIDEOS",
        videoId: null,
        videoTitle: null,
        createTime,
      },
    ],
    messages: {
      1: [
        {
          id: 1,
          role: "USER",
          content: "这个视频讲了什么？",
          createTime,
        },
        {
          id: 2,
          role: "ASSISTANT",
          content: "它主要讲了如何用 Spring AI Alibaba 读取 B 站字幕、切分文本并写入向量库，再围绕这些内容构建问答能力。[1]",
          createTime,
          sources: [mockSource()],
        },
      ],
      2: [
        {
          id: 3,
          role: "USER",
          content: "删除视频时需要清理哪些数据？",
          createTime,
        },
        {
          id: 4,
          role: "ASSISTANT",
          content: "需要删除视频主记录、分片记录、向量映射、DashVector 中对应的向量，以及相关单视频会话。",
          createTime,
        },
      ],
    },
  };
}

function readDatabase() {
  const raw = localStorage.getItem(DB_KEY);
  if (!raw) {
    const seeded = defaultDatabase();
    writeDatabase(seeded);
    return seeded;
  }

  try {
    const parsed = JSON.parse(raw);
    parsed.nextImportBatchId ||= 1;
    parsed.importBatches ||= [];
    return parsed;
  } catch {
    const seeded = defaultDatabase();
    writeDatabase(seeded);
    return seeded;
  }
}

function writeDatabase(db) {
  localStorage.setItem(DB_KEY, JSON.stringify(db));
}

function delay(ms = 220) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function refreshMockBatch(batch) {
  const count = (status) => batch.items.filter((item) => item.status === status).length;
  batch.totalCount = batch.items.length;
  batch.queuedCount = count("QUEUED");
  batch.runningCount = count("RUNNING");
  batch.succeededCount = count("SUCCEEDED");
  batch.skippedCount = count("SKIPPED");
  batch.failedCount = count("FAILED");
  batch.updateTime = nowString();
  if (batch.queuedCount || batch.runningCount) {
    batch.status = "RUNNING";
    batch.finishTime = null;
  } else {
    batch.status = batch.failedCount ? "PARTIAL_FAILED" : "COMPLETED";
    batch.finishTime = nowString();
  }
  return batch;
}

function advanceMockBatch(db, batch) {
  const nextItems = batch.items.filter((item) => item.status === "QUEUED").slice(0, 2);
  nextItems.forEach((item) => {
    item.startTime = nowString();
    if (!item.bvid) {
      item.status = "FAILED";
      item.failReason = "无法解析 BV 号";
    } else {
      item.status = "SUCCEEDED";
      item.failReason = null;
      let video = db.videos.find((candidate) => candidate.bvid === item.bvid);
      if (!video) {
        video = {
          id: db.nextVideoId++,
          bvid: item.bvid,
          title: `开发模式视频 ${item.bvid}`,
          description: "由批量导入开发模式生成。",
          chunkCount: 32,
          importTime: nowString(),
          status: "SUCCESS",
          failReason: null,
        };
        db.videos.unshift(video);
      }
      item.videoId = video.id;
    }
    item.finishTime = nowString();
  });
  return refreshMockBatch(batch);
}

function batchSummary(batch) {
  return { ...batch, items: [] };
}

function batchItemId(batchId, index) {
  return batchId * 1000 + index + 1;
}

function buildAssistantReply(session, prompt) {
  const baseReply =
    session.sessionType === "SINGLE_VIDEO"
      ? "当前会话限定在单视频范围内，系统会按照 user_id 和 bvid 过滤相关字幕分片，再结合上下文组织回答。"
      : "当前会话限定在全部视频范围内，系统会按 user_id 检索当前用户导入的全部视频内容，再汇总生成答案。";

  return `${baseReply} [1] 你刚刚的问题是“${prompt}”。在开发模式下，这段回复来自前端本地 mock SSE 流。`;
}

function mockSource() {
  return {
    index: 1,
    bvid: "BV1iH3763Ezm",
    videoTitle: "开发模式字幕来源",
    pageNumber: 1,
    startTimeMs: 130000,
    endTimeMs: 156000,
    snippet: "这是用于验证回答来源卡片和时间跳转的字幕片段。",
    jumpUrl: "https://www.bilibili.com/video/BV1iH3763Ezm/?p=1&t=127.5",
  };
}

export const devServer = {
  async importVideo(payload) {
    await delay();
    const db = readDatabase();
    const bvid = extractBvid(payload.bvidOrUrl);

    if (!bvid) {
      throw createError(ERROR_CODES.BVID_PARSE_ERROR, "无法从输入内容中解析 BV 号。");
    }

    if (db.videos.some((item) => item.bvid === bvid)) {
      throw createError(ERROR_CODES.VIDEO_ALREADY_EXISTS, "该视频已存在，请先删除后再导入。");
    }

    const video = {
      id: db.nextVideoId++,
      bvid,
      title: `开发模式视频 ${bvid}`,
      description: "该记录由前端本地开发模式生成，用于测试导入、会话和问答流程。",
      chunkCount: 24 + Math.floor(Math.random() * 24),
      importTime: nowString(),
      status: "SUCCESS",
      failReason: null,
    };

    db.videos.unshift(video);
    writeDatabase(db);
    return video;
  },

  async createVideoImportBatch(payload) {
    await delay();
    const db = readDatabase();
    const seen = new Set();
    const createTime = nowString();
    const batchId = db.nextImportBatchId++;
    const batch = {
      id: batchId,
      status: "RUNNING",
      createTime,
      updateTime: createTime,
      finishTime: null,
      items: payload.inputs.map((input, index) => {
        const bvid = extractBvid(input);
        const duplicate = bvid && seen.has(bvid);
        const existing = bvid && db.videos.some((video) => video.bvid === bvid);
        if (bvid) seen.add(bvid);
        const status = !bvid ? "FAILED" : duplicate || existing ? "SKIPPED" : "QUEUED";
        return {
          id: batchItemId(batchId, index),
          originalInput: input,
          bvid: bvid || null,
          status,
          failReason: !bvid ? "无法解析 BV 号" : duplicate ? "批次内重复" : existing ? "视频已导入" : null,
          retryCount: 0,
          videoId: null,
          createTime,
          startTime: null,
          finishTime: status === "QUEUED" ? null : createTime,
        };
      }),
    };
    refreshMockBatch(batch);
    db.importBatches.unshift(batch);
    writeDatabase(db);
    return JSON.parse(JSON.stringify(batch));
  },

  async listVideoImportBatches() {
    await delay();
    const db = readDatabase();
    return db.importBatches.slice(0, 20).map(batchSummary);
  },

  async getVideoImportBatch(id) {
    await delay();
    const db = readDatabase();
    const batch = db.importBatches.find((candidate) => candidate.id === Number(id));
    if (!batch) throw createError(2006, "导入批次不存在。");
    if (batch.status === "RUNNING") advanceMockBatch(db, batch);
    writeDatabase(db);
    return JSON.parse(JSON.stringify(batch));
  },

  async retryFailedVideoImports(id) {
    await delay();
    const db = readDatabase();
    const batch = db.importBatches.find((candidate) => candidate.id === Number(id));
    if (!batch) throw createError(2006, "导入批次不存在。");
    batch.items.filter((item) => item.status === "FAILED").forEach((item) => {
      item.status = "QUEUED";
      item.failReason = null;
      item.retryCount += 1;
      item.startTime = null;
      item.finishTime = null;
    });
    refreshMockBatch(batch);
    writeDatabase(db);
    return JSON.parse(JSON.stringify(batch));
  },

  async listVideos() {
    await delay();
    const db = readDatabase();
    return [...db.videos];
  },

  async getVideo(id) {
    await delay();
    const db = readDatabase();
    const video = db.videos.find((item) => item.id === Number(id));
    if (!video) {
      throw createError(ERROR_CODES.VIDEO_NOT_FOUND, "视频不存在或已被删除。");
    }
    return video;
  },

  async removeVideo(id) {
    await delay();
    const db = readDatabase();
    const targetId = Number(id);
    const exists = db.videos.some((item) => item.id === targetId);
    if (!exists) {
      throw createError(ERROR_CODES.VIDEO_NOT_FOUND, "视频不存在或已被删除。");
    }

    db.videos = db.videos.filter((item) => item.id !== targetId);
    const removedSessionIds = db.sessions.filter((item) => item.videoId === targetId).map((item) => item.id);
    db.sessions = db.sessions.filter((item) => item.videoId !== targetId);
    removedSessionIds.forEach((sessionId) => {
      delete db.messages[sessionId];
    });
    writeDatabase(db);
    return null;
  },

  async listSessions() {
    await delay();
    const db = readDatabase();
    return [...db.sessions];
  },

  async createSession(payload) {
    await delay();
    const db = readDatabase();

    if (payload.sessionType !== "SINGLE_VIDEO" && payload.sessionType !== "ALL_VIDEOS") {
      throw createError(ERROR_CODES.SESSION_TYPE_ERROR, "会话类型不合法，请重新创建会话。");
    }

    let videoTitle = null;
    let videoId = null;

    if (payload.sessionType === "SINGLE_VIDEO") {
      if (!payload.videoId) {
        throw createError(ERROR_CODES.PARAM_ERROR, "单视频对话必须选择目标视频。");
      }
      const video = db.videos.find((item) => item.id === Number(payload.videoId));
      if (!video) {
        throw createError(ERROR_CODES.VIDEO_NOT_FOUND, "目标视频不存在。");
      }
      videoId = video.id;
      videoTitle = video.title;
    }

    const session = {
      id: db.nextSessionId++,
      sessionType: payload.sessionType,
      videoId,
      videoTitle,
      createTime: nowString(),
    };

    db.sessions.unshift(session);
    db.messages[session.id] = [];
    writeDatabase(db);
    return session;
  },

  async getSession(id) {
    await delay();
    const db = readDatabase();
    const session = db.sessions.find((item) => item.id === Number(id));
    if (!session) {
      throw createError(ERROR_CODES.SESSION_NOT_FOUND, "会话不存在或已被删除。");
    }
    return session;
  },

  async removeSession(id) {
    await delay();
    const db = readDatabase();
    const targetId = Number(id);
    const exists = db.sessions.some((item) => item.id === targetId);
    if (!exists) {
      throw createError(ERROR_CODES.SESSION_NOT_FOUND, "会话不存在或已被删除。");
    }
    db.sessions = db.sessions.filter((item) => item.id !== targetId);
    delete db.messages[targetId];
    writeDatabase(db);
    return null;
  },

  async listMessages(sessionId) {
    await delay();
    const db = readDatabase();
    const session = db.sessions.find((item) => item.id === Number(sessionId));
    if (!session) {
      throw createError(ERROR_CODES.SESSION_NOT_FOUND, "会话不存在或已被删除。");
    }
    return [...(db.messages[sessionId] || [])];
  },

  async streamMessage(sessionId, payload, handlers = {}, signal) {
    const db = readDatabase();
    const session = db.sessions.find((item) => item.id === Number(sessionId));
    if (!session) {
      throw createError(ERROR_CODES.SESSION_NOT_FOUND, "会话不存在或已被删除。");
    }
    if (!payload.content) {
      throw createError(ERROR_CODES.PARAM_ERROR, "消息内容不能为空。");
    }

    const userMessage = {
      id: db.nextMessageId++,
      role: "USER",
      content: payload.content,
      createTime: nowString(),
    };
    db.messages[session.id] = [...(db.messages[session.id] || []), userMessage];
    writeDatabase(db);

    if (handlers.start) {
      await handlers.start({
        type: "start",
        userMessageId: userMessage.id,
      });
    }

    const fullReply = buildAssistantReply(session, payload.content);
    const chunks = fullReply.match(/.{1,9}/g) || [fullReply];
    let built = "";

    for (const chunk of chunks) {
      if (signal?.aborted) {
        const abortError = new Error("已取消流式响应");
        abortError.name = "AbortError";
        throw abortError;
      }
      built += chunk;
      await delay(120);
      if (handlers.content) {
        await handlers.content({
          type: "content",
          delta: chunk,
        });
      }
    }

    const latestDb = readDatabase();
    const assistantMessage = {
      id: latestDb.nextMessageId++,
      role: "ASSISTANT",
      content: built,
      createTime: nowString(),
      sources: [mockSource()],
    };
    latestDb.messages[session.id] = [...(latestDb.messages[session.id] || []), assistantMessage];
    writeDatabase(latestDb);

    if (handlers.end) {
      await handlers.end({
        type: "end",
        userMessageId: userMessage.id,
        assistantMessageId: assistantMessage.id,
        fullContent: built,
        sources: assistantMessage.sources,
      });
    }
  },
};
