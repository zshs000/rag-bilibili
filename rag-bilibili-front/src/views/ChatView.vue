<template>
  <AppShell
    eyebrow="Streaming Chat"
    title="检索增强问答"
    subtitle="围绕当前会话持续提问。系统会结合你导入的内容逐步生成回答，你可以继续追问细节、总结观点或回顾重点。"
  >
    <template #header-actions>
      <div class="toolbar">
        <el-button @click="reloadSession" :loading="loading">刷新会话</el-button>
        <RouterLink to="/sessions">
          <el-button>返回会话列表</el-button>
        </RouterLink>
      </div>
    </template>

    <div class="page-grid">
      <section class="surface span-8 card-section">
        <div class="card-header">
          <div>
            <div class="eyebrow">Conversation</div>
            <h2>{{ session?.videoTitle || "全部视频知识库" }}</h2>
            <p class="card-caption">
              {{
                session?.sessionType === "SINGLE_VIDEO"
                  ? "当前仅检索指定视频的字幕分片。"
                  : "当前检索当前用户全部已导入视频。"
              }}
            </p>
          </div>
          <StatusPill :label="streaming ? '生成中' : '可提问'" :tone="streaming ? 'warning' : 'success'" />
        </div>

        <el-alert
          v-if="inlineError"
          class="alert-inline"
          type="error"
          :title="inlineError"
          show-icon
          role="alert"
        />

        <div v-if="messages.length" class="message-list">
          <article v-for="message in messages" :key="message.localKey" class="surface-strong message-item">
            <div class="list-card-top">
              <StatusPill :label="roleMeta(message.role).label" :tone="roleMeta(message.role).tone" />
              <span class="muted">{{ formatDateTime(message.createTime) }}</span>
            </div>
            <MarkdownContent
              v-if="message.role === 'ASSISTANT'"
              :content="message.content || '...'"
              class="message-content"
            />
            <p v-else class="message-content">{{ message.content || "..." }}</p>
          </article>
        </div>

        <EmptyState
          v-else
          badge="CHAT"
          title="还没有消息"
          description="先提出一个你真正关心的问题，例如视频核心观点、实现步骤、删除规则或知识总结。"
        />

        <div class="surface-strong card-section top-gap">
          <div class="card-header">
            <div>
              <div class="eyebrow">Composer</div>
              <h3>发送问题</h3>
              <p class="card-caption">建议一次只问一个重点问题，回答会更聚焦，也更方便继续追问。</p>
            </div>
            <div class="toolbar">
              <el-button v-if="streaming" type="danger" plain @click="abortStream">停止生成</el-button>
            </div>
          </div>

          <el-form @submit.prevent="sendMessage">
            <el-form-item label="问题内容">
              <el-input
                v-model.trim="draft"
                type="textarea"
                :rows="5"
                resize="none"
                maxlength="2000"
                show-word-limit
                placeholder="例如：这个视频讲了什么？"
              />
            </el-form-item>
            <div class="toolbar">
              <el-button type="primary" :loading="streaming" @click="sendMessage">开始提问</el-button>
            </div>
          </el-form>
        </div>
      </section>

      <section class="surface span-4 card-section">
        <div class="card-header">
          <div>
            <div class="eyebrow">Session Meta</div>
            <h2>会话信息</h2>
          </div>
        </div>

        <div v-if="session" class="stack">
          <div class="surface-strong card-section">
            <div class="eyebrow">使用说明</div>
            <div class="stack">
              <div><strong>问答范围：</strong>{{ session.sessionType === "SINGLE_VIDEO" ? "单个视频" : "全部视频" }}</div>
              <div><strong>视频标题：</strong>{{ session.videoTitle || "全部视频知识库" }}</div>
              <div><strong>创建时间：</strong>{{ formatDateTime(session.createTime) }}</div>
            </div>
          </div>

          <div class="surface-strong card-section">
            <div class="eyebrow">提问建议</div>
            <div class="stack top-gap">
              <div>先问“这个视频主要讲了什么”，快速了解大意。</div>
              <div>再问“有哪些关键步骤、限制或删除规则”，补齐细节。</div>
              <div>如果是全视频会话，适合做横向对比和总结归纳。</div>
              <div>回答不够聚焦时，可以换成更短、更具体的问题。</div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </AppShell>
</template>

<script setup>
import { ElMessage } from "element-plus";
import { onBeforeUnmount, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

import AppShell from "../components/AppShell.vue";
import EmptyState from "../components/EmptyState.vue";
import StatusPill from "../components/StatusPill.vue";
import MarkdownContent from "../components/MarkdownContent.vue";
import { messagesApi } from "../api/messages";
import { sessionsApi } from "../api/sessions";
import { MESSAGE_ROLE_META } from "../constants/options";
import { notifyError } from "../utils/error";
import { formatDateTime } from "../utils/format";
import { logger } from "../utils/logger";

const route = useRoute();
const router = useRouter();
const sessionId = Number(route.params.sessionId);

const session = ref(null);
const messages = ref([]);
const loading = ref(false);
const streaming = ref(false);
const inlineError = ref("");
const draft = ref("");
let abortController = null;
let streamThrottleTimer = null;
let pendingAssistantMessage = null;
let pendingAssistantDelta = "";

loadSession();

onBeforeUnmount(() => {
  abortStream();
  clearPendingStreamFrame();
});

function roleMeta(role) {
  return MESSAGE_ROLE_META[role] || { label: role || "UNKNOWN", tone: "warning" };
}

function withLocalKey(message) {
  return {
    localKey: message.id || `${message.role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    ...message,
  };
}

function patchMessage(localKey, updater) {
  const index = messages.value.findIndex((item) => item.localKey === localKey);
  if (index < 0) {
    return null;
  }

  const current = messages.value[index];
  const next = typeof updater === "function" ? updater(current) : { ...current, ...updater };
  messages.value[index] = next;
  return next;
}

async function loadSession() {
  if (!Number.isInteger(sessionId) || sessionId <= 0) {
    inlineError.value = "当前会话地址无效，已返回会话列表。";
    await router.replace({ name: "sessions" });
    return;
  }

  loading.value = true;
  inlineError.value = "";
  try {
    const [sessionDetail, messageList] = await Promise.all([
      sessionsApi.detail(sessionId),
      messagesApi.list(sessionId),
    ]);
    session.value = sessionDetail;
    messages.value = messageList.map((item) => withLocalKey(item));
  } catch (error) {
    inlineError.value = notifyError(error).message;
  } finally {
    loading.value = false;
  }
}

async function reloadSession() {
  await loadSession();
}

function abortStream() {
  if (abortController) {
    abortController.abort();
    abortController = null;
  }
  flushPendingAssistantContent();
  streaming.value = false;
}

function clearPendingStreamFrame() {
  if (!streamThrottleTimer) {
    return;
  }
  clearTimeout(streamThrottleTimer);
  streamThrottleTimer = null;
}

function flushPendingAssistantContent() {
  clearPendingStreamFrame();
  if (!pendingAssistantMessage || !pendingAssistantDelta) {
    pendingAssistantDelta = "";
    return;
  }

  pendingAssistantMessage = patchMessage(pendingAssistantMessage.localKey, (current) => ({
    ...current,
    content: `${current.content || ""}${pendingAssistantDelta}`,
  }));
  pendingAssistantDelta = "";
}

function scheduleAssistantContent(message, delta) {
  pendingAssistantMessage = message;
  pendingAssistantDelta += delta;

  if (streamThrottleTimer) {
    return;
  }

  streamThrottleTimer = setTimeout(() => {
    streamThrottleTimer = null;
    flushPendingAssistantContent();
  }, 100);
}

async function sendMessage() {
  inlineError.value = "";

  const currentDraft = draft.value.trim();
  if (!currentDraft) {
    inlineError.value = "消息内容不能为空。";
    return;
  }

  const previousDraft = draft.value;
  const userMessage = withLocalKey({
    id: null,
    role: "USER",
    content: currentDraft,
    createTime: new Date().toISOString(),
  });
  const assistantMessage = withLocalKey({
    id: null,
    role: "ASSISTANT",
    content: "",
    createTime: new Date().toISOString(),
  });

  messages.value.push(userMessage, assistantMessage);
  streaming.value = true;
  abortController = new AbortController();
  let hasStarted = false;
  let hasCompleted = false;

  try {
    await messagesApi.stream(
      sessionId,
      { content: currentDraft },
      {
        start(payload) {
          hasStarted = true;
          patchMessage(userMessage.localKey, (current) => ({
            ...current,
            id: payload.userMessageId || current.id,
          }));
          draft.value = "";
          logger.info("chat", "收到 start 事件", payload);
        },
        content(payload) {
          if (!payload.delta) {
            return;
          }
          scheduleAssistantContent(assistantMessage, payload.delta);
        },
        end(payload) {
          flushPendingAssistantContent();
          hasCompleted = true;
          pendingAssistantMessage = patchMessage(assistantMessage.localKey, (current) => ({
            ...current,
            id: payload.assistantMessageId || current.id,
            content: payload.fullContent || current.content,
          }));
          logger.info("chat", "收到 end 事件", payload);
        },
        error(payload) {
          throw new Error(payload?.message || "流式回答出现异常。");
        },
      },
      abortController.signal
    );
    const latestMessages = await messagesApi.list(sessionId);
    messages.value = latestMessages.map((item) => withLocalKey(item));
    ElMessage.success("回答生成完成");
  } catch (error) {
    flushPendingAssistantContent();
    if (error?.name === "AbortError") {
      if (!hasStarted) {
        messages.value = messages.value.filter(
          (item) => item.localKey !== userMessage.localKey && item.localKey !== assistantMessage.localKey
        );
        draft.value = previousDraft;
      } else if (!hasCompleted) {
        patchMessage(assistantMessage.localKey, (current) => ({
          ...current,
          content: current.content || "当前回答已停止显示，你可以刷新会话后继续查看。",
        }));
      }
      ElMessage.warning("已停止当前流式响应");
    } else {
      const normalized = notifyError(error);
      inlineError.value = normalized.message;
      if (!hasStarted) {
        messages.value = messages.value.filter(
          (item) => item.localKey !== userMessage.localKey && item.localKey !== assistantMessage.localKey
        );
        draft.value = previousDraft;
      } else {
        patchMessage(assistantMessage.localKey, (current) => ({
          ...current,
          content: current.content || "回答生成中断，请稍后刷新会话再试。",
        }));
      }
    }
  } finally {
    pendingAssistantMessage = null;
    pendingAssistantDelta = "";
    clearPendingStreamFrame();
    streaming.value = false;
    abortController = null;
  }
}
</script>

<style scoped>
.message-content {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
