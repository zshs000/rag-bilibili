<template>
  <el-dialog
    :model-value="modelValue"
    title="新建会话"
    width="560px"
    @close="emit('update:modelValue', false)"
  >
    <el-form ref="formRef" :model="form" label-position="top">
      <el-form-item label="会话类型" prop="sessionType">
        <el-radio-group v-model="form.sessionType">
          <el-radio-button v-for="item in SESSION_TYPE_OPTIONS" :key="item.value" :label="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item
        v-if="form.sessionType === 'SINGLE_VIDEO'"
        label="目标视频"
        prop="videoId"
        :error="videoError"
      >
        <el-select v-model="form.videoId" placeholder="请选择一个已导入视频" class="full-width">
          <el-option v-for="video in videos" :key="video.id" :label="video.title" :value="video.id">
            <span>{{ video.title }}</span>
            <span class="muted code-text">{{ video.bvid }}</span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="toolbar">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">创建并进入对话</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";

import { SESSION_TYPE_OPTIONS } from "../constants/options";

const props = defineProps({
  modelValue: {
    type: Boolean,
    required: true,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  videos: {
    type: Array,
    default: () => [],
  },
  presetVideoId: {
    type: Number,
    default: null,
  },
});

const emit = defineEmits(["update:modelValue", "submit"]);

const formRef = ref();
const form = reactive({
  sessionType: "SINGLE_VIDEO",
  videoId: null,
});

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      form.sessionType = props.presetVideoId ? "SINGLE_VIDEO" : "ALL_VIDEOS";
      form.videoId = props.presetVideoId || null;
    }
  },
  { immediate: true }
);

const videoError = computed(() =>
  form.sessionType === "SINGLE_VIDEO" && !form.videoId ? "单视频对话必须选择一个视频" : ""
);

function handleSubmit() {
  if (videoError.value) {
    return;
  }
  emit("submit", {
    sessionType: form.sessionType,
    videoId: form.sessionType === "SINGLE_VIDEO" ? form.videoId : null,
  });
}
</script>
