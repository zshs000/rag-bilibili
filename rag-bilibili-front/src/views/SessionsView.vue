<template>
  <AppShell
    eyebrow="Sessions"
    title="历史会话管理"
    subtitle="你可以为单个视频创建专注型会话，也可以创建覆盖全部资料的综合会话。选好范围后，继续深入提问即可。"
  >
    <template #header-actions>
      <div class="toolbar">
        <el-button type="primary" @click="openCreateDialog()">新建会话</el-button>
        <el-button @click="loadData" :loading="loading">刷新</el-button>
      </div>
    </template>

    <div class="page-grid">
      <section class="surface span-4 card-section">
        <div class="card-header">
          <div>
            <div class="eyebrow">Session Stats</div>
            <h2>概览</h2>
          </div>
        </div>
        <div class="stack">
          <div class="surface-strong stat-card">
            <strong>{{ sessions.length }}</strong>
            <p>全部会话数量</p>
          </div>
          <div class="surface-strong stat-card">
            <strong>{{ singleCount }}</strong>
            <p>单视频对话</p>
          </div>
          <div class="surface-strong stat-card">
            <strong>{{ allVideosCount }}</strong>
            <p>全部视频对话</p>
          </div>
        </div>
      </section>

      <section class="surface span-8 card-section">
        <div class="card-header">
          <div>
            <div class="eyebrow">Conversations</div>
            <h2>会话列表</h2>
            <p class="card-caption">如果你想聚焦某一个视频，就创建单视频会话；如果想跨视频比较或总结，就创建全视频会话。</p>
          </div>
          <el-input v-model.trim="keyword" placeholder="搜索会话标题" clearable style="width: 240px" />
        </div>

        <el-alert v-if="inlineError" class="alert-inline" type="error" :title="inlineError" show-icon />

        <div v-if="filteredSessions.length" class="session-list">
          <article v-for="session in filteredSessions" :key="session.id" class="surface-strong list-card">
            <div class="list-card-top">
              <div>
                <h3>{{ session.videoTitle || "全部视频知识库" }}</h3>
                <p>
                  {{
                    session.sessionType === "SINGLE_VIDEO"
                      ? "检索范围限定为单个视频。"
                      : "检索范围限定为当前用户导入的全部视频。"
                  }}
                </p>
              </div>
              <StatusPill
                :label="session.sessionType === 'SINGLE_VIDEO' ? '单视频' : '全视频'"
                :tone="session.sessionType === 'SINGLE_VIDEO' ? 'success' : 'warning'"
              />
            </div>

            <div class="list-card-bottom top-gap">
              <div class="flow-meta">
                <span class="badge-inline">
                  {{ session.sessionType === "SINGLE_VIDEO" ? "围绕单个视频" : "覆盖全部资料" }}
                </span>
                <span class="badge-inline">{{ formatDateTime(session.createTime) }}</span>
              </div>
              <div class="toolbar">
                <el-button type="primary" plain @click="openChat(session.id)">进入对话</el-button>
                <el-button type="danger" plain @click="removeSession(session)">删除</el-button>
              </div>
            </div>
          </article>
        </div>

        <EmptyState
          v-else
          badge="SESSIONS"
          title="当前没有会话"
          description="先创建一个单视频或全视频会话，再开始问答。"
        >
          <el-button type="primary" @click="openCreateDialog()">立即创建</el-button>
        </EmptyState>
      </section>
    </div>

    <SessionDialog
      v-model="dialogVisible"
      :videos="availableVideos"
      :loading="creating"
      :preset-video-id="presetVideoId"
      @submit="handleCreateSession"
    />
  </AppShell>
</template>

<script setup>
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, ref } from "vue";
import { useRouter } from "vue-router";

import AppShell from "../components/AppShell.vue";
import EmptyState from "../components/EmptyState.vue";
import SessionDialog from "../components/SessionDialog.vue";
import StatusPill from "../components/StatusPill.vue";
import { sessionsApi } from "../api/sessions";
import { videosApi } from "../api/videos";
import { notifyError } from "../utils/error";
import { formatDateTime } from "../utils/format";

const router = useRouter();

const sessions = ref([]);
const availableVideos = ref([]);
const loading = ref(false);
const creating = ref(false);
const inlineError = ref("");
const dialogVisible = ref(false);
const presetVideoId = ref(null);
const keyword = ref("");

const filteredSessions = computed(() => {
  const query = keyword.value.toLowerCase();
  if (!query) {
    return sessions.value;
  }
  return sessions.value.filter((item) => (item.videoTitle || "全部视频知识库").toLowerCase().includes(query));
});

const singleCount = computed(() => sessions.value.filter((item) => item.sessionType === "SINGLE_VIDEO").length);
const allVideosCount = computed(() => sessions.value.filter((item) => item.sessionType === "ALL_VIDEOS").length);

loadData();

async function loadData() {
  loading.value = true;
  inlineError.value = "";

  try {
    const [sessionList, videoList] = await Promise.all([sessionsApi.list(), videosApi.list()]);
    sessions.value = sessionList;
    availableVideos.value = videoList.filter((item) => item.status === "SUCCESS");
  } catch (error) {
    inlineError.value = notifyError(error).message;
  } finally {
    loading.value = false;
  }
}

function openCreateDialog(videoId = null) {
  presetVideoId.value = videoId;
  dialogVisible.value = true;
}

async function handleCreateSession(payload) {
  creating.value = true;
  try {
    const session = await sessionsApi.create(payload);
    ElMessage.success("会话创建成功");
    dialogVisible.value = false;
    await loadData();
    await router.push({ name: "chat", params: { sessionId: session.id } });
  } catch (error) {
    notifyError(error);
  } finally {
    creating.value = false;
  }
}

async function removeSession(session) {
  try {
    await ElMessageBox.confirm(`确定删除这个会话吗？`, "删除会话", {
      confirmButtonText: "确认删除",
      cancelButtonText: "取消",
      type: "warning",
    });
    await sessionsApi.remove(session.id);
    ElMessage.success("会话已删除");
    await loadData();
  } catch (error) {
    if (error !== "cancel") {
      notifyError(error);
    }
  }
}

async function openChat(sessionId) {
  await router.push({ name: "chat", params: { sessionId } });
}
</script>
