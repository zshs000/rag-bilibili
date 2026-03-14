<template>
  <div class="markdown-content" v-html="renderedHtml"></div>
</template>

<script setup>
import { computed } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";

const props = defineProps({
  content: {
    type: String,
    default: "",
  },
});

// 配置marked
marked.setOptions({
  breaks: true,
  gfm: true,
});

const renderedHtml = computed(() => {
  if (!props.content) {
    return "";
  }
  try {
    const rawHtml = marked.parse(props.content);
    // 使用DOMPurify净化HTML，防止XSS攻击
    return DOMPurify.sanitize(rawHtml, {
      ALLOWED_TAGS: [
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
        'p', 'br', 'hr',
        'ul', 'ol', 'li',
        'a', 'strong', 'em', 'code', 'pre',
        'blockquote', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
        'img', 'span', 'div'
      ],
      ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class'],
      ALLOW_DATA_ATTR: false,
    });
  } catch (error) {
    console.error("Markdown解析失败:", error);
    return DOMPurify.sanitize(props.content);
  }
});
</script>

<style scoped>
.markdown-content {
  line-height: 1.6;
  word-wrap: break-word;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin-top: 1.5em;
  margin-bottom: 0.5em;
  font-weight: 600;
}

.markdown-content :deep(h1) {
  font-size: 1.8em;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h2) {
  font-size: 1.5em;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h3) {
  font-size: 1.3em;
}

.markdown-content :deep(p) {
  margin-bottom: 1em;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin-bottom: 1em;
  padding-left: 2em;
}

.markdown-content :deep(li) {
  margin-bottom: 0.25em;
}

.markdown-content :deep(code) {
  background-color: rgba(255, 255, 255, 0.08);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: "Consolas", "Monaco", "Courier New", monospace;
  font-size: 0.9em;
  color: #ff6b35;
}

.markdown-content :deep(pre) {
  background-color: rgba(26, 31, 46, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 1em;
  border-radius: 5px;
  overflow-x: auto;
  margin-bottom: 1em;
}

.markdown-content :deep(pre code) {
  background-color: transparent;
  padding: 0;
}

.markdown-content :deep(blockquote) {
  border-left: 4px solid rgba(255, 107, 53, 0.5);
  padding-left: 1em;
  margin-left: 0;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 1em;
}

.markdown-content :deep(a) {
  color: #4a90e2;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin-bottom: 1em;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 0.5em;
  text-align: left;
}

.markdown-content :deep(th) {
  background-color: rgba(255, 255, 255, 0.05);
  font-weight: 600;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
}

.markdown-content :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.15);
  margin: 1.5em 0;
}

/* Light Theme Overrides */
:root[data-theme="light"] .markdown-content :deep(h1),
:root[data-theme="light"] .markdown-content :deep(h2) {
  border-bottom-color: #e5e7eb;
}

:root[data-theme="light"] .markdown-content :deep(code) {
  background-color: #f3f4f6;
  color: #ff6b35;
}

:root[data-theme="light"] .markdown-content :deep(pre) {
  background-color: #f3f4f6;
  border-color: #e5e7eb;
}

:root[data-theme="light"] .markdown-content :deep(blockquote) {
  border-left-color: #e5e7eb;
  color: #6b7280;
}

:root[data-theme="light"] .markdown-content :deep(th),
:root[data-theme="light"] .markdown-content :deep(td) {
  border-color: #e5e7eb;
}

:root[data-theme="light"] .markdown-content :deep(th) {
  background-color: #f3f4f6;
}

:root[data-theme="light"] .markdown-content :deep(hr) {
  border-top-color: #e5e7eb;
}
</style>
