<template>
  <div class="batch-layout">
    <section class="surface card-section">
      <div class="card-header">
        <div>
          <div class="eyebrow">Batch Import</div>
          <h2>多行批量导入</h2>
          <p class="card-caption">每行填写一个 BV 号或视频链接，单批最多 50 个，后台同时处理 2 个。</p>
        </div>
        <StatusPill :label="`${inputCount}/50`" :tone="inputCount > 50 ? 'danger' : 'success'" />
      </div>

      <el-alert v-if="inlineError" class="alert-inline" type="error" :title="inlineError" show-icon />

      <el-form label-position="top">
        <el-form-item label="视频列表">
          <el-input
            v-model="form.inputText"
            type="textarea"
            :rows="9"
            resize="vertical"
            placeholder="BV1DCfsBKExV&#10;https://www.bilibili.com/video/BV1iH3763Ezm"
          />
        </el-form-item>
        <el-form-item label="SESSDATA">
          <el-input v-model.trim="form.sessdata" type="password" show-password />
        </el-form-item>
        <el-form-item label="bili_jct">
          <el-input v-model.trim="form.biliJct" type="password" show-password />
        </el-form-item>
        <el-form-item label="buvid3">
          <el-input v-model.trim="form.buvid3" type="password" show-password />
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button type="primary" :loading="submitting" @click="createBatch">创建批量任务</el-button>
        <el-button @click="clearCredentials">清除凭证</el-button>
      </div>
      <p class="card-caption top-gap">凭证只发送给后端执行本批任务，不会写入浏览器存储或显示在任务历史中。</p>
    </section>

    <section class="surface card-section">
      <div class="card-header">
        <div>
          <div class="eyebrow">Progress</div>
          <h2>批次进度</h2>
        </div>
        <StatusPill v-if="selectedBatch" :label="batchStatusLabel(selectedBatch.status)" :tone="batchTone(selectedBatch.status)" />
      </div>

      <template v-if="selectedBatch">
        <el-progress :percentage="progressPercentage" :status="selectedBatch.failedCount ? 'warning' : undefined" />
        <div class="summary-grid top-gap">
          <span>成功 {{ selectedBatch.succeededCount }}</span>
          <span>处理中 {{ selectedBatch.runningCount }}</span>
          <span>排队 {{ selectedBatch.queuedCount }}</span>
          <span>跳过 {{ selectedBatch.skippedCount }}</span>
          <span>失败 {{ selectedBatch.failedCount }}</span>
        </div>

        <div class="toolbar top-gap">
          <el-button
            v-if="selectedBatch.failedCount"
            type="primary"
            plain
            :loading="retrying"
            @click="retryFailed"
          >仅重试失败项</el-button>
          <el-button @click="refreshSelected">刷新</el-button>
        </div>

        <div class="item-list top-gap">
          <article v-for="item in selectedBatch.items" :key="item.id" class="surface-strong item-card">
            <div class="item-title-row">
              <strong class="item-input">{{ item.bvid || item.originalInput }}</strong>
              <StatusPill :label="itemStatusLabel(item.status)" :tone="itemTone(item.status)" />
            </div>
            <p v-if="item.failReason" class="card-caption top-gap">{{ item.failReason }}</p>
          </article>
        </div>
      </template>
      <p v-else class="card-caption">创建任务或从下方历史记录中选择一个批次。</p>
    </section>

    <section class="surface card-section batch-history">
      <div class="card-header">
        <div>
          <div class="eyebrow">History</div>
          <h2>最近批次</h2>
        </div>
        <el-button text :loading="loadingHistory" @click="loadHistory">刷新</el-button>
      </div>
      <div v-if="history.length" class="history-list">
        <button
          v-for="batch in history"
          :key="batch.id"
          type="button"
          class="history-row"
          :class="{ active: selectedBatch?.id === batch.id }"
          @click="selectBatch(batch.id)"
        >
          <span>#{{ batch.id }} · {{ batch.createTime }}</span>
          <span>{{ batchStatusLabel(batch.status) }} · {{ finishedCount(batch) }}/{{ batch.totalCount }}</span>
        </button>
      </div>
      <p v-else class="card-caption">暂无批量导入记录。</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";

import { videoImportBatchesApi } from "../api/video-import-batches";
import { notifyError } from "../utils/error";
import StatusPill from "./StatusPill.vue";

const POLL_INTERVAL = 2000;
const form = reactive({ inputText: "", sessdata: "", biliJct: "", buvid3: "" });
const inlineError = ref("");
const submitting = ref(false);
const retrying = ref(false);
const loadingHistory = ref(false);
const history = ref([]);
const selectedBatch = ref(null);
let pollTimer = null;

const inputs = computed(() => form.inputText.split(/\r?\n/).map((value) => value.trim()).filter(Boolean));
const inputCount = computed(() => inputs.value.length);
const progressPercentage = computed(() => {
  if (!selectedBatch.value?.totalCount) return 0;
  return Math.round((finishedCount(selectedBatch.value) / selectedBatch.value.totalCount) * 100);
});

onMounted(loadHistory);
onBeforeUnmount(stopPolling);

async function createBatch() {
  inlineError.value = "";
  if (!inputCount.value || !form.sessdata || !form.biliJct || !form.buvid3) {
    inlineError.value = "请填写至少一个视频和 3 个凭证字段。";
    return;
  }
  if (inputCount.value > 50) {
    inlineError.value = "单批最多导入 50 个视频。";
    return;
  }
  submitting.value = true;
  try {
    selectedBatch.value = await videoImportBatchesApi.create({
      inputs: inputs.value,
      sessdata: form.sessdata,
      biliJct: form.biliJct,
      buvid3: form.buvid3,
    });
    ElMessage.success("批量导入任务已创建");
    await loadHistory();
    startPolling();
  } catch (error) {
    inlineError.value = notifyError(error).message;
  } finally {
    submitting.value = false;
  }
}

async function loadHistory() {
  loadingHistory.value = true;
  try {
    history.value = await videoImportBatchesApi.list();
  } catch (error) {
    notifyError(error);
  } finally {
    loadingHistory.value = false;
  }
}

async function selectBatch(id) {
  stopPolling();
  try {
    selectedBatch.value = await videoImportBatchesApi.detail(id);
    if (selectedBatch.value.status === "RUNNING") startPolling();
  } catch (error) {
    notifyError(error);
  }
}

async function refreshSelected() {
  if (!selectedBatch.value) return;
  try {
    selectedBatch.value = await videoImportBatchesApi.detail(selectedBatch.value.id);
    if (selectedBatch.value.status !== "RUNNING") {
      stopPolling();
      await loadHistory();
    }
  } catch (error) {
    stopPolling();
    notifyError(error);
  }
}

async function retryFailed() {
  retrying.value = true;
  try {
    selectedBatch.value = await videoImportBatchesApi.retryFailed(selectedBatch.value.id);
    ElMessage.success("失败项已重新排队");
    startPolling();
  } catch (error) {
    notifyError(error);
  } finally {
    retrying.value = false;
  }
}

function startPolling() {
  stopPolling();
  if (selectedBatch.value?.status !== "RUNNING") return;
  pollTimer = window.setInterval(refreshSelected, POLL_INTERVAL);
}

function stopPolling() {
  if (pollTimer) window.clearInterval(pollTimer);
  pollTimer = null;
}

function clearCredentials() {
  form.sessdata = "";
  form.biliJct = "";
  form.buvid3 = "";
  ElMessage.success("凭证已清除");
}

function finishedCount(batch) {
  return (batch.succeededCount || 0) + (batch.skippedCount || 0) + (batch.failedCount || 0);
}

function batchStatusLabel(status) {
  return { RUNNING: "处理中", COMPLETED: "已完成", PARTIAL_FAILED: "部分失败" }[status] || status;
}

function itemStatusLabel(status) {
  return { QUEUED: "排队中", RUNNING: "导入中", SUCCEEDED: "成功", SKIPPED: "已跳过", FAILED: "失败" }[status] || status;
}

function batchTone(status) {
  return status === "COMPLETED" ? "success" : status === "PARTIAL_FAILED" ? "danger" : "info";
}

function itemTone(status) {
  if (status === "SUCCEEDED") return "success";
  if (status === "FAILED") return "danger";
  if (status === "SKIPPED") return "warning";
  return "info";
}
</script>

<style scoped>
.batch-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; }
.batch-history { grid-column: 1 / -1; }
.summary-grid { display: flex; flex-wrap: wrap; gap: 10px 18px; color: var(--rb-text-soft); }
.item-list, .history-list { display: grid; gap: 10px; max-height: 420px; overflow: auto; }
.item-card { padding: 14px; }
.item-title-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.item-input { min-width: 0; overflow-wrap: anywhere; }
.history-row { width: 100%; display: flex; justify-content: space-between; gap: 16px; padding: 13px 14px; border: 1px solid var(--rb-border); border-radius: 12px; background: transparent; color: inherit; cursor: pointer; text-align: left; }
.history-row:hover, .history-row.active { border-color: var(--rb-accent); background: var(--rb-panel-strong); }
@media (max-width: 900px) { .batch-layout { grid-template-columns: 1fr; } .batch-history { grid-column: auto; } }
</style>
