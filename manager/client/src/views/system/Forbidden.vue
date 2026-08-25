<template>
  <div class="forbidden tech-card">
    <div class="code tech-mono">403</div>
    <div class="text">无权限访问该页面</div>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<script setup>
// 403 无权限占位页。
</script>

<style scoped>
.forbidden {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  height: 60vh;
  text-align: center;
}
.code {
  font-size: 64px;
  color: var(--tech-accent);
  text-shadow: var(--tech-glow);
}
.text {
  color: var(--el-text-color-regular);
}
</style>
