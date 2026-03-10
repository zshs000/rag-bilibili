<template>
  <AppShell
    eyebrow="Video Library"
    title="已导入视频列表"
    subtitle="在这里管理已经整理好的视频资料。你可以查看内容概况、删除旧资料，或者直接围绕某个视频开始提问。"
  >
    <template #header-actions>
      <div class="toolbar">
        <el-input v-model.trim="keyword" placeholder="搜索标题或 BV 号" clearable style="width: 240px" />
        <el-button @click="loadVideos" :loading="loading">刷新列表</el-button>
      </div>
    </template>

    <div class="page-grid">
      <section class="surface span-12 card-section">
        <div class="stats-grid">
          <div class="surface-strong stat-card">
            <div class="eyebrow">Total</div>
            <strong>{{ videos.length }}</strong>
            <p>当前用户已导入的视频总数。</p>
          </div>
          <div class="surface-strong stat-card">
            <div class="eyebrow">Ready</div>
            <strong>{{ readyCount }}</strong>
            <p>状态为 SUCCESS，可直接创建会话和问答。</p>
          </div>
          <div class="surface-strong stat-card">
            <div class="eyebrow">Failed</div>
            <strong>{{ failedCount }}</strong>
            <p>状态为 FAILED，建议查看失败原因后删除重试。</p>
          </div>
        </div>
      </section>

      <section class="surface span-12 card-section">
        <div class="card-header">
          <div>
            <div class="eyebrow">Library</div>
            <h2>视频库</h2>
            <p class="card-caption">建议先看状态是否可用，再选择进入问答或继续补充更多视频。</p>
          </div>
          <div class="toolbar">
            <RouterLink to="/import">
              <el-button type="primary">继续导入视频</el-button>
            </RouterLink>
          </div>
        </div>

        <el-alert v-if="inlineError" class="alert-inline" type="error" :title="inlineError" show-icon />

        <div v-if="filteredVideos.length" class="video-list">
          <article v-for="video in filteredVideos" :key="video.id" class="surface-strong list-card">
            <div class="list-card-top">
              <div>
                <h3>{{ video.title }}</h3>
                <p>{{ clampText(video.description, 180) || "暂无视频简介" }}</p>
              </div>
              <StatusPill :label="statusMeta(video.status).label" :tone="statusMeta(video.status).tone" />
            </div>

            <div class="list-card-bottom top-gap">
              <div class="flow-meta">
                <span class="badge-inline code-text">{{ video.bvid }}</span>
                <span class="badge-inline">内容片段 {{ video.chunkCount ?? "--" }}</span>
                <span class="badge-inline">{{ formatDateTime(video.importTime) }}</span>
              </div>
              <div class="toolbar">
                <el-button @click="showVideoDetail(video.id)">详情</el-button>
                <el-button type="primary" plain :disabled="video.status !== 'SUCCESS'" @click="createSingleSession(video)">
                  进入问答
                </el-button>
                <el-button type="danger" plain @click="deleteVideo(video)">删除</el-button>
              </div>
            </div>

            <el-alert
              v-if="video.status === 'FAILED' && video.failReason"
              class="top-gap"
              type="warning"
              :title="video.failReason"
              show-icon
            />
          </article>
        </div>

        <EmptyState
          v-else
          badge="VIDEOS"
          title="还没有匹配的视频"
          :description="videos.length ? '当前搜索条件下没有结果。' : '先去导入一个 B 站视频，再回到这里管理知识库。'"
        >
          <RouterLink to="/import">
            <el-button type="primary">去导入视频</el-button>
          </RouterLink>
        </EmptyState>
      </section>
    </div>

    <el-drawer v-model="detailVisible" title="视频详情" size="520px">
      <div v-loading="detailLoading" class="stack">
        <template v-if="selectedVideo">
          <div class="surface-strong card-section">
            <div class="eyebrow">Video Detail</div>
            <h3>{{ selectedVideo.title }}</h3>
            <p class="card-caption">{{ selectedVideo.description || "暂无简介" }}</p>
          </div>
          <div class="surface-strong card-section">
            <div class="stack">
              <div><strong>BV 号：</strong><span class="code-text">{{ selectedVideo.bvid }}</span></div>
              <div><strong>状态：</strong>{{ statusMeta(selectedVideo.status).label }}</div>
              <div><strong>分片数：</strong>{{ selectedVideo.chunkCount ?? "--" }}</div>
              <div><strong>导入时间：</strong>{{ formatDateTime(selectedVideo.importTime) }}</div>
              <div v-if="selectedVideo.failReason"><strong>失败原因：</strong>{{ selectedVideo.failReason }}</div>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>
  </AppShell>
</template>

<script setup>
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";

import AppShell from "../components/AppShell.vue";
import EmptyState from "../components/EmptyState.vue";
import StatusPill from "../components/StatusPill.vue";
import { sessionsApi } from "../api/sessions";
import { videosApi } from "../api/videos";
import { VIDEO_STATUS_META } from "../constants/options";
import { notifyError } from "../utils/error";
import { clampText, formatDateTime } from "../utils/format";

const router = useRouter();

const videos = ref([]);
const loading = ref(false);
const inlineError = ref("");
const keyword = ref("");
const detailVisible = ref(false);
const detailLoading = ref(false);
const selectedVideo = ref(null);

const filteredVideos = computed(() => {
  const query = keyword.value.toLowerCase();
  if (!query) {
    return videos.value;
  }
  return videos.value.filter(
    (video) =>
      video.title?.toLowerCase().includes(query) ||
      video.bvid?.toLowerCase().includes(query)
  );
});

const readyCount = computed(() => videos.value.filter((item) => item.status === "SUCCESS").length);
const failedCount = computed(() => videos.value.filter((item) => item.status === "FAILED").length);

loadVideos();

function statusMeta(status) {
  return VIDEO_STATUS_META[status] || { label: status || "--", tone: "warning" };
}

async function loadVideos() {
  loading.value = true;
  inlineError.value = "";
  try {
    videos.value = await videosApi.list();
  } catch (error) {
    inlineError.value = notifyError(error).message;
  } finally {
    loading.value = false;
  }
}

async function showVideoDetail(id) {
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    selectedVideo.value = await videosApi.detail(id);
  } catch (error) {
    detailVisible.value = false;
    notifyError(error);
  } finally {
    detailLoading.value = false;
  }
}

async function createSingleSession(video) {
  try {
    const session = await sessionsApi.create({
      sessionType: "SINGLE_VIDEO",
      videoId: video.id,
    });
    ElMessage.success("会话创建成功");
    await router.push({ name: "chat", params: { sessionId: session.id } });
  } catch (error) {
    notifyError(error);
  }
}

async function deleteVideo(video) {
  try {
    await ElMessageBox.confirm(
      `确定删除《${video.title}》吗？该操作会联动删除向量数据和相关单视频会话。`,
      "删除视频",
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning",
      }
    );
    await videosApi.remove(video.id);
    ElMessage.success("视频已删除");
    await loadVideos();
  } catch (error) {
    if (error !== "cancel") {
      notifyError(error);
    }
  }
}
</script>
