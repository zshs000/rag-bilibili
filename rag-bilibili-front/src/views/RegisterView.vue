<template>
  <div class="auth-shell">
    <section class="surface auth-hero">
      <div class="eyebrow">RAG-BILIBILI / REGISTER</div>
      <h1>建立你的私有视频知识工作台。</h1>
      <p>
        用户注册后将拥有独立数据空间，视频、向量映射、会话与消息都必须按用户维度隔离。
        当前页面只提交文档要求的最小注册字段，并对输入不合法时给出明确提示。
      </p>

      <div class="stats-grid top-gap">
        <div class="surface-strong stat-card">
          <div class="eyebrow">01</div>
          <strong>用户隔离</strong>
          <p>所有后续查询、删除和问答都带用户维度。</p>
        </div>
        <div class="surface-strong stat-card">
          <div class="eyebrow">02</div>
          <strong>清晰报错</strong>
          <p>对 `USER_ALREADY_EXISTS`、参数错误等场景显示可理解信息。</p>
        </div>
        <div class="surface-strong stat-card">
          <div class="eyebrow">03</div>
          <strong>快速进入</strong>
          <p>注册成功后直接跳转登录页，避免身份状态混乱。</p>
        </div>
      </div>
    </section>

    <section class="surface auth-panel">
      <div class="eyebrow">Create Account</div>
      <h2>注册</h2>
      <p class="muted">用户名长度需在 3-50 之间，密码长度需在 6-20 之间。</p>

      <el-alert v-if="inlineError" class="alert-inline" type="error" :title="inlineError" show-icon />

      <el-form :model="form" label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="用户名">
          <el-input v-model.trim="form.username" placeholder="3-50 个字符" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model.trim="form.password" type="password" show-password placeholder="6-20 个字符" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model.trim="form.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入密码"
          />
        </el-form-item>
        <div class="toolbar">
          <el-button type="primary" :loading="authStore.loading" @click="handleSubmit">注册</el-button>
          <RouterLink to="/login">
            <el-button>返回登录</el-button>
          </RouterLink>
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { ElMessage } from "element-plus";
import { reactive, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";

import { useAuthStore } from "../stores/auth";
import { notifyError } from "../utils/error";

const router = useRouter();
const authStore = useAuthStore();

const inlineError = ref("");
const form = reactive({
  username: "",
  password: "",
  confirmPassword: "",
});

async function handleSubmit() {
  inlineError.value = "";

  if (form.username.length < 3 || form.username.length > 50) {
    inlineError.value = "用户名长度必须在 3-50 之间。";
    return;
  }
  if (form.password.length < 6 || form.password.length > 20) {
    inlineError.value = "密码长度必须在 6-20 之间。";
    return;
  }
  if (form.password !== form.confirmPassword) {
    inlineError.value = "两次输入的密码不一致。";
    return;
  }

  try {
    await authStore.register({
      username: form.username,
      password: form.password,
    });
    ElMessage.success("注册成功，请登录");
    await router.push({ name: "login" });
  } catch (error) {
    inlineError.value = notifyError(error).message;
  }
}
</script>
