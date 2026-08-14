<template>
  <div v-if="sources.length" class="message-sources" aria-label="回答来源">
    <div class="source-heading">回答来源</div>
    <div class="source-list">
      <a
        v-for="source in sources"
        :key="source.index"
        class="source-card"
        :href="source.jumpUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        <span class="source-index">[{{ source.index }}]</span>
        <span class="source-detail">
          <strong>{{ source.videoTitle || source.bvid }}</strong>
          <span>{{ formatSourcePosition(source) }}</span>
          <span class="source-snippet">{{ source.snippet }}</span>
        </span>
      </a>
    </div>
  </div>
</template>

<script setup>
defineProps({
  sources: {
    type: Array,
    default: () => [],
  },
});

function formatSourcePosition(source) {
  const start = formatTimestamp(source.startTimeMs);
  const end = formatTimestamp(source.endTimeMs);
  return `P${source.pageNumber || 1} · ${start}–${end}`;
}

function formatTimestamp(timeMs) {
  const seconds = Math.max(0, Number(timeMs || 0) / 1000);
  const minutes = Math.floor(seconds / 60);
  const remaining = Math.floor(seconds % 60).toString().padStart(2, "0");
  return `${minutes}:${remaining}`;
}
</script>

<style scoped>
.message-sources {
  margin-top: 0.75rem;
}

.source-heading {
  margin-bottom: 0.5rem;
  color: var(--rb-text-soft);
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.source-list {
  display: grid;
  gap: 0.5rem;
}

.source-card {
  display: flex;
  gap: 0.65rem;
  padding: 0.7rem;
  border: 1px solid var(--rb-border);
  border-radius: 10px;
  color: inherit;
  text-decoration: none;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.source-card:hover {
  border-color: var(--rb-accent);
  transform: translateY(-1px);
}

.source-index {
  color: var(--rb-accent);
  font-weight: 700;
}

.source-detail {
  display: grid;
  min-width: 0;
  gap: 0.2rem;
  color: var(--rb-text-soft);
  font-size: 0.85rem;
}

.source-detail strong {
  color: var(--rb-text);
}

.source-snippet {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
