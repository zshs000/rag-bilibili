<template>
  <div class="source-browser">
    <section class="surface card-section source-config">
      <div class="card-header">
        <div>
          <div class="eyebrow">Discover</div>
          <h2>选择视频来源</h2>
          <p class="card-caption">先浏览和勾选视频，本阶段不会开始导入。</p>
        </div>
        <StatusPill :label="sourceMode === 'favorite' ? '我的收藏夹' : 'UP 主投稿'" tone="info" />
      </div>

      <el-radio-group v-model="sourceMode" @change="resetResults">
        <el-radio-button value="favorite">我的收藏夹</el-radio-button>
        <el-radio-button value="up">UP 主投稿</el-radio-button>
      </el-radio-group>

      <el-alert
        v-if="inlineError"
        class="alert-inline top-gap"
        type="error"
        :title="inlineError"
        show-icon
        role="alert"
      />

      <el-form class="source-form top-gap" label-position="top">
        <template v-if="sourceMode === 'up'">
          <el-form-item label="UP 主 UID 或空间链接">
            <el-input v-model.trim="upInput" placeholder="例如 1045711541 或 https://space.bilibili.com/1045711541" />
          </el-form-item>
          <el-form-item label="访问方式">
            <el-switch
              v-model="useCredentials"
              active-text="携带登录凭证"
              inactive-text="匿名访问"
            />
          </el-form-item>
          <p class="card-caption credential-hint">
            公开投稿可以先尝试匿名访问；充电、登录可见内容或遇到风控时再开启登录凭证。
          </p>
        </template>

        <div v-if="sourceMode === 'favorite' || useCredentials" class="credential-grid">
          <el-form-item label="SESSDATA">
            <el-input v-model.trim="credentials.sessdata" type="password" show-password autocomplete="off" />
          </el-form-item>
          <el-form-item label="bili_jct">
            <el-input v-model.trim="credentials.biliJct" type="password" show-password autocomplete="off" />
          </el-form-item>
          <el-form-item label="buvid3">
            <el-input v-model.trim="credentials.buvid3" type="password" show-password autocomplete="off" />
          </el-form-item>
        </div>

        <el-form-item v-if="sourceMode === 'favorite' && folders.length" label="收藏夹">
          <el-select v-model="folderId" class="folder-select" @change="loadFirstPage">
            <el-option
              v-for="folder in folders"
              :key="folder.id"
              :label="`${folder.title}（${folder.mediaCount}）`"
              :value="folder.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button type="primary" :loading="loading" @click="startBrowse">
          {{ sourceMode === "favorite" && !folders.length ? "获取收藏夹" : "获取视频" }}
        </el-button>
        <el-button v-if="sourceMode === 'favorite' && folders.length" :loading="loadingFolders" @click="loadFolders">
          刷新收藏夹
        </el-button>
        <el-button v-if="sourceMode === 'favorite' || useCredentials" @click="clearCredentials">清除凭证</el-button>
      </div>
    </section>

    <section class="surface card-section source-results" aria-live="polite">
      <div class="card-header results-header">
        <div>
          <div class="eyebrow">Preview</div>
          <h2>视频列表</h2>
          <p class="card-caption">
            {{ pageData ? `共 ${pageData.total} 个视频` : "获取来源后在这里选择视频" }}
          </p>
        </div>
        <div class="selected-summary">已选择 <strong>{{ selectedVideos.size }}</strong> 个</div>
      </div>

      <div v-if="pageData?.items?.length" class="selection-toolbar">
        <el-checkbox
          :model-value="allCurrentSelected"
          :indeterminate="someCurrentSelected"
          @change="toggleCurrentPage"
        >当前页全选</el-checkbox>
        <span class="card-caption">本页 {{ selectableItems.length }} 个可选</span>
      </div>

      <el-skeleton v-if="loadingVideos" :rows="6" animated />

      <div v-else-if="pageData?.items?.length" class="video-source-list">
        <article
          v-for="video in pageData.items"
          :key="video.bvid || `${video.title}-${video.publishTime}`"
          class="video-source-row"
          :class="{ unavailable: video.unavailable }"
        >
          <el-checkbox
            class="video-checkbox"
            :model-value="selectedVideos.has(video.bvid)"
            :disabled="video.unavailable"
            :aria-label="`选择 ${video.title}`"
            @change="(checked) => toggleVideo(video, checked)"
          />
          <div class="cover-wrap">
            <img
              v-if="video.coverUrl"
              :src="video.coverUrl"
              :alt="video.title"
              loading="lazy"
              referrerpolicy="no-referrer"
              @error="hideBrokenImage"
            />
            <div class="cover-fallback">BILI</div>
            <span class="duration-badge">{{ formatDuration(video.durationSeconds) }}</span>
          </div>
          <div class="video-copy">
            <div class="video-title-line">
              <h3>{{ video.title || "未命名视频" }}</h3>
              <StatusPill v-if="video.unavailable" label="不可用" tone="danger" />
            </div>
            <p class="card-caption">{{ video.ownerName || "未知 UP" }} · UID {{ video.ownerMid || "--" }}</p>
            <div class="video-meta">
              <span>{{ formatPublishTime(video.publishTime) }}</span>
              <span class="code-text">{{ video.bvid || "无 BV 号" }}</span>
            </div>
          </div>
        </article>
      </div>

      <el-empty v-else-if="pageData" description="这个来源当前没有可展示的视频" />
      <div v-else class="results-placeholder">
        <div class="placeholder-mark">BV</div>
        <p>选择收藏夹或输入 UP 主后获取视频。</p>
      </div>

      <el-pagination
        v-if="pageData && pageData.total > pageSize"
        class="source-pagination"
        background
        layout="prev, pager, next"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="pageData.total"
        @current-change="changePage"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { ElMessage } from "element-plus";

import { bilibiliSourcesApi } from "../api/bilibili-sources";
import { notifyError } from "../utils/error";
import StatusPill from "./StatusPill.vue";

const pageSize = 20;
const sourceMode = ref("favorite");
const upInput = ref("");
const useCredentials = ref(false);
const credentials = reactive({ sessdata: "", biliJct: "", buvid3: "" });
const folders = ref([]);
const folderId = ref(null);
const pageData = ref(null);
const currentPage = ref(1);
const selectedVideos = ref(new Map());
const loadingFolders = ref(false);
const loadingVideos = ref(false);
const inlineError = ref("");
let requestGeneration = 0;

const loading = computed(() => loadingFolders.value || loadingVideos.value);
const selectableItems = computed(() => (pageData.value?.items || []).filter((video) => !video.unavailable && video.bvid));
const selectedOnCurrentPage = computed(() => selectableItems.value.filter((video) => selectedVideos.value.has(video.bvid)).length);
const allCurrentSelected = computed(() => selectableItems.value.length > 0 && selectedOnCurrentPage.value === selectableItems.value.length);
const someCurrentSelected = computed(() => selectedOnCurrentPage.value > 0 && !allCurrentSelected.value);

async function startBrowse() {
  inlineError.value = "";
  if (sourceMode.value === "favorite") {
    if (!hasCredentials()) {
      inlineError.value = "查看我的收藏夹需要完整填写三项 B站凭证。";
      return;
    }
    if (!folders.value.length) {
      await loadFolders();
      return;
    }
  } else if (!upInput.value) {
    inlineError.value = "请输入 UP 主 UID 或空间链接。";
    return;
  } else if (useCredentials.value && !hasCredentials()) {
    inlineError.value = "登录访问需要完整填写三项 B站凭证。";
    return;
  }
  await loadFirstPage();
}

async function loadFolders() {
  const generation = ++requestGeneration;
  inlineError.value = "";
  if (!hasCredentials()) {
    inlineError.value = "查看我的收藏夹需要完整填写三项 B站凭证。";
    return;
  }
  loadingFolders.value = true;
  try {
    const result = await bilibiliSourcesApi.favoriteFolders({ ...credentials });
    if (generation !== requestGeneration || sourceMode.value !== "favorite") return;
    folders.value = result;
    folderId.value = folders.value[0]?.id ?? null;
    if (folderId.value) {
      loadingFolders.value = false;
      await loadFirstPage();
    } else {
      pageData.value = { page: 1, pageSize, total: 0, hasMore: false, items: [] };
    }
  } catch (error) {
    if (generation !== requestGeneration) return;
    inlineError.value = notifyError(error).message;
  } finally {
    if (generation === requestGeneration) loadingFolders.value = false;
  }
}

async function loadFirstPage() {
  currentPage.value = 1;
  await loadVideos(++requestGeneration);
}

async function loadVideos(generation = ++requestGeneration) {
  inlineError.value = "";
  loadingVideos.value = true;
  try {
    let result;
    if (sourceMode.value === "favorite") {
      if (!folderId.value) {
        pageData.value = null;
        inlineError.value = "请先获取并选择收藏夹。";
        return;
      }
      result = await bilibiliSourcesApi.favoriteVideos(folderId.value, {
        page: currentPage.value,
        pageSize,
        ...credentials,
      });
    } else {
      const payload = {
        up: upInput.value,
        page: currentPage.value,
        pageSize,
        useCredentials: useCredentials.value,
      };
      if (useCredentials.value) Object.assign(payload, credentials);
      result = await bilibiliSourcesApi.upVideos(payload);
    }
    if (generation !== requestGeneration) return;
    pageData.value = result;
  } catch (error) {
    if (generation !== requestGeneration) return;
    pageData.value = null;
    inlineError.value = notifyError(error).message;
  } finally {
    if (generation === requestGeneration) loadingVideos.value = false;
  }
}

async function changePage(page) {
  currentPage.value = page;
  await loadVideos(++requestGeneration);
}

function resetResults() {
  requestGeneration += 1;
  loadingFolders.value = false;
  loadingVideos.value = false;
  folders.value = [];
  folderId.value = null;
  pageData.value = null;
  currentPage.value = 1;
  inlineError.value = "";
  selectedVideos.value = new Map();
}

function toggleVideo(video, checked) {
  const next = new Map(selectedVideos.value);
  if (checked) next.set(video.bvid, video);
  else next.delete(video.bvid);
  selectedVideos.value = next;
}

function toggleCurrentPage(checked) {
  const next = new Map(selectedVideos.value);
  selectableItems.value.forEach((video) => {
    if (checked) next.set(video.bvid, video);
    else next.delete(video.bvid);
  });
  selectedVideos.value = next;
}

function clearCredentials() {
  credentials.sessdata = "";
  credentials.biliJct = "";
  credentials.buvid3 = "";
  ElMessage.success("凭证已清除");
}

function hasCredentials() {
  return Boolean(credentials.sessdata && credentials.biliJct && credentials.buvid3);
}

function formatDuration(totalSeconds) {
  const seconds = Math.max(0, Number(totalSeconds) || 0);
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remain = seconds % 60;
  return hours
    ? `${hours}:${String(minutes).padStart(2, "0")}:${String(remain).padStart(2, "0")}`
    : `${minutes}:${String(remain).padStart(2, "0")}`;
}

function formatPublishTime(epochSeconds) {
  if (!epochSeconds) return "发布时间未知";
  return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" })
    .format(new Date(epochSeconds * 1000));
}

function hideBrokenImage(event) {
  event.currentTarget.style.display = "none";
}
</script>

<style scoped>
.source-browser { display: grid; grid-template-columns: minmax(300px, 0.82fr) minmax(0, 1.58fr); gap: 18px; align-items: start; }
.source-config { position: sticky; top: 18px; }
.source-form { display: grid; gap: 2px; }
.credential-grid { display: grid; grid-template-columns: 1fr; gap: 2px; }
.credential-hint { margin: -8px 0 14px; }
.folder-select { width: 100%; }
.results-header { align-items: center; }
.selected-summary { padding: 9px 13px; border-radius: 999px; background: var(--rb-accent-soft); color: var(--rb-text); white-space: nowrap; }
.selected-summary strong { color: var(--rb-accent); }
.selection-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 0; border-top: 1px solid var(--rb-border); border-bottom: 1px solid var(--rb-border); }
.video-source-list { display: grid; }
.video-source-row { display: grid; grid-template-columns: auto 172px minmax(0, 1fr); gap: 16px; align-items: center; padding: 16px 0; border-bottom: 1px solid var(--rb-border); }
.video-source-row.unavailable { opacity: 0.58; }
.video-checkbox { margin-right: 0; }
.cover-wrap { position: relative; aspect-ratio: 16 / 9; overflow: hidden; border-radius: 12px; background: var(--rb-panel-strong); }
.cover-wrap img { position: relative; z-index: 1; width: 100%; height: 100%; object-fit: cover; display: block; }
.cover-fallback { position: absolute; inset: 0; display: grid; place-items: center; font-weight: 800; color: var(--rb-text-soft); letter-spacing: 0.12em; }
.duration-badge { position: absolute; z-index: 2; right: 7px; bottom: 7px; padding: 2px 6px; border-radius: 6px; background: rgba(0, 0, 0, 0.76); color: white; font-size: 12px; }
.video-copy { min-width: 0; }
.video-title-line { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.video-title-line h3 { margin: 0; font-size: 16px; line-height: 1.5; overflow-wrap: anywhere; }
.video-meta { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-top: 10px; color: var(--rb-text-soft); font-size: 13px; }
.results-placeholder { min-height: 360px; display: grid; place-content: center; justify-items: center; gap: 14px; color: var(--rb-text-soft); text-align: center; }
.placeholder-mark { width: 72px; height: 72px; display: grid; place-items: center; border-radius: 22px; background: var(--rb-accent-soft); color: var(--rb-accent); font-weight: 800; font-size: 22px; }
.source-pagination { justify-content: center; margin-top: 20px; }
@media (max-width: 980px) { .source-browser { grid-template-columns: 1fr; } .source-config { position: static; } }
@media (max-width: 620px) { .video-source-row { grid-template-columns: auto 112px minmax(0, 1fr); gap: 10px; } .video-title-line h3 { font-size: 14px; } .video-meta { display: none; } }
</style>
