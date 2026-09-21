<script setup lang="ts">
const status = '工程骨架已就绪'
</script>

<template>
  <main class="shell">
    <h1>AfterSales Copilot</h1>
    <p>{{ status }}</p>
    <p class="muted">Vue 3 + TypeScript + Vite</p>
  </main>
</template>

<style scoped>
.shell { max-width: 720px; margin: 15vh auto; padding: 2rem; font-family: system-ui, sans-serif; }
.muted { color: #64748b; }
</style>
