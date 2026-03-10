<template>
  <AppShell
    eyebrow="Video Import"
    title="把单个 B 站视频导入到知识库"
    subtitle="填写视频标识和必要凭证后，即可把视频内容整理成可检索、可提问的知识资料。"
  >

    <div class="page-grid">
      <section class="surface span-7 card-section">
        <div class="card-header">
          <div>
            <div class="eyebrow">Start Here</div>
            <h2>导入请求</h2>
            <p class="card-caption">支持输入 BV 号，或包含 BV 的完整 Bilibili 视频 URL。</p>
          </div>
          <StatusPill label="准备导入" tone="success" />
        </div>

        <el-alert
          v-if="inlineError"
          class="alert-inline"
          type="error"
          :title="inlineError"
          show-icon
          role="alert"
        />

        <el-form label-position="top">
          <el-form-item label="BV 号或视频 URL">
            <el-input
              v-model.trim="form.bvidOrUrl"
              placeholder="例如 BV1DCfsBKExV 或 https://www.bilibili.com/video/BV1DCfsBKExV"
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

        <div class="surface-strong card-section">
          <div class="eyebrow">使用说明</div>
          <p class="card-caption">
            1. 先填写视频 BV 号或视频链接。2. 再补充导入所需凭证。3. 导入完成后，去视频列表查看结果并开始问答。
          </p>
          <div class="toolbar top-gap">
            <el-button type="primary" :loading="submitting" @click="handleSubmit">开始导入</el-button>
            <el-button @click="clearCredentials">清除凭证</el-button>
          </div>
        </div>
      </section>

      <section class="surface span-5 card-section">
        <div class="card-header">
          <div>
            <div class="eyebrow">Feedback</div>
            <h2>执行反馈</h2>
          </div>
        </div>

        <div class="stack">
          <div class="surface-strong card-section">
            <strong>请求状态</strong>
            <p class="card-caption top-gap">{{ requestState }}</p>
          </div>
          <div class="surface-strong card-section">
            <strong>填写建议</strong>
            <p class="card-caption top-gap">
              为了保护账号信息，凭证只会临时保留在当前浏览器标签页中，方便你短时间内重复导入。
            </p>
          </div>
          <div v-if="result" class="surface-strong card-section">
            <strong>导入结果</strong>
            <div class="top-gap flow-meta">
              <StatusPill :label="result.status || 'SUCCESS'" tone="success" />
              <span class="badge-inline code-text">{{ result.bvid }}</span>
            </div>
            <p class="card-caption top-gap">{{ result.title }}</p>
            <p class="card-caption">内容片段 {{ result.chunkCount }} / 时间: {{ formatDateTime(result.importTime) }}</p>
            <div class="toolbar top-gap">
              <RouterLink to="/videos">
                <el-button type="primary" plain>查看视频列表</el-button>
              </RouterLink>
            </div>
          </div>
        </div>
      </section>
    </div>
  </AppShell>
</template>

<script setup>
import { ElMessage } from "element-plus";
import { reactive, ref, watch } from "vue";
import { RouterLink } from "vue-router";

import AppShell from "../components/AppShell.vue";
import StatusPill from "../components/StatusPill.vue";
import { videosApi } from "../api/videos";
import { formatDateTime } from "../utils/format";
import { notifyError } from "../utils/error";

const STORAGE_KEY = "rag-bilibili-credentials";

const inlineError = ref("");
const submitting = ref(false);
const requestState = ref("等待提交。");
const result = ref(null);
const form = reactive({
  bvidOrUrl: "",
  sessdata: "",
  biliJct: "",
  buvid3: "",
});

loadCredentials();

watch(
  () => ({ ...form }),
  () => {
    saveCredentials();
  },
  { deep: true }
);

function saveCredentials() {
  const payload = {
    sessdata: form.sessdata,
    biliJct: form.biliJct,
    buvid3: form.buvid3,
  };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
}

function loadCredentials() {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return;
  }
  try {
    const payload = JSON.parse(raw);
    form.sessdata = payload.sessdata || "";
    form.biliJct = payload.biliJct || "";
    form.buvid3 = payload.buvid3 || "";
  } catch {
    sessionStorage.removeItem(STORAGE_KEY);
  }
}

function clearCredentials() {
  form.sessdata = "";
  form.biliJct = "";
  form.buvid3 = "";
  sessionStorage.removeItem(STORAGE_KEY);
  ElMessage.success("凭证已清除");
}

async function handleSubmit() {
  inlineError.value = "";
  result.value = null;

  if (!form.bvidOrUrl || !form.sessdata || !form.biliJct || !form.buvid3) {
    inlineError.value = "请完整填写 BV/URL 与 3 个凭证字段。";
    return;
  }

  submitting.value = true;
  requestState.value = "正在整理视频内容，请稍候。";

  try {
    result.value = await videosApi.importVideo(form);
    requestState.value = "导入完成，返回结果已写入当前页面。";
    ElMessage.success("导入成功");
  } catch (error) {
    inlineError.value = notifyError(error).message;
    requestState.value = "导入失败，请检查填写内容后重试。";
  } finally {
    submitting.value = false;
  }
}
</script>
