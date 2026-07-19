<template>
  <el-container style="height: 100vh">
    <el-aside width="200px" class="aside">
      <div class="logo">rustto</div>
      <el-menu :default-active="active" router>
        <el-menu-item index="/dashboard">概览</el-menu-item>
        <el-menu-item index="/nodes">节点管理</el-menu-item>
        <el-menu-item index="/tasks">备份任务</el-menu-item>
        <el-menu-item index="/records">备份记录</el-menu-item>
        <el-menu-item index="/binaries">二进制版本</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="user">{{ auth.username }}</span>
        <el-button text @click="onLogout">退出</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

/** 当前激活菜单（用于高亮）。 */
const active = computed(() => route.path);

/**
 * 退出登录。
 */
function onLogout() {
  auth.logout();
  router.push('/login');
}
</script>

<style scoped>
.aside {
  background: #001529;
}
.logo {
  height: 60px;
  color: #fff;
  font-size: 20px;
  font-weight: bold;
  line-height: 60px;
  text-align: center;
}
.aside .el-menu {
  border-right: none;
}
.header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.user {
  color: #333;
}
</style>
