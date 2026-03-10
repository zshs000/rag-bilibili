<template>
  <div class="empty-block surface-strong">
    <StatusPill :label="badge" tone="warning" />
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>

<script setup>
import StatusPill from "./StatusPill.vue";

defineProps({
  badge: {
    type: String,
    default: "EMPTY",
  },
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    required: true,
  },
});
</script>
