export const SESSION_TYPE_OPTIONS = [
  { label: "单视频对话", value: "SINGLE_VIDEO" },
  { label: "全部视频对话", value: "ALL_VIDEOS" },
];

export const VIDEO_STATUS_META = {
  IMPORTING: {
    label: "导入中",
    tone: "warning",
  },
  SUCCESS: {
    label: "可用",
    tone: "success",
  },
  FAILED: {
    label: "失败",
    tone: "danger",
  },
};

export const MESSAGE_ROLE_META = {
  USER: {
    label: "USER",
    tone: "user",
  },
  ASSISTANT: {
    label: "ASSISTANT",
    tone: "assistant",
  },
};
