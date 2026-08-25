<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside tech-card">
      <div class="logo">
        <span class="logo-mark">◆</span>
        <span class="logo-text">restto</span>
      </div>
      <el-menu
        :default-active="active"
        router
        class="side-menu"
        background-color="transparent"
        text-color="#b8c7dc"
        active-text-color="#00e5ff"
        unique-opened
      >
        <side-menu :nodes="auth.menus" />
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="crumb tech-mono">{{ route.meta.title || '控制台' }}</div>
        <el-dropdown @command="onCommand">
          <span class="user">
            <span class="avatar">{{ initial }}</span>
            <span class="name">{{ auth.nickname || auth.username }}</span>
            <el-tag v-if="auth.isAdmin" size="small" effect="dark" class="role-tag">超管</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import SideMenu from '../components/SideMenu.vue';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

/** 当前激活菜单（用于高亮）。 */
const active = computed(() => route.path);

/** 用户名首字母做头像。 */
const initial = computed(() => (auth.username || '?').slice(0, 1).toUpperCase());

/**
 * 下拉菜单命令处理。
 *
 * @param {string} cmd 命令
 */
function onCommand(cmd) {
  if (cmd === 'logout') {
    auth.logout();
    router.push('/login');
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--tech-border);
  border-radius: 0;
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.4);
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 18px;
  border-bottom: 1px solid var(--tech-border);
}
.logo-mark {
  color: var(--tech-accent);
  text-shadow: var(--tech-glow);
  font-size: 18px;
}
.logo-text {
  font-family: var(--tech-font-mono);
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--el-text-color-primary);
}
.side-menu {
  flex: 1;
  padding: 8px 0;
  overflow-y: auto;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(180deg, rgba(13, 21, 38, 0.9), rgba(10, 14, 26, 0.6));
  border-bottom: 1px solid var(--tech-border);
}
.crumb {
  color: var(--tech-accent);
  letter-spacing: 1px;
  text-transform: uppercase;
  font-size: 13px;
}
.user {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  color: var(--el-text-color-primary);
}
.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(0, 229, 255, 0.12);
  border: 1px solid var(--tech-accent);
  color: var(--tech-accent);
  font-family: var(--tech-font-mono);
  font-weight: 700;
}
.role-tag {
  background: rgba(124, 92, 255, 0.18);
  border-color: var(--tech-accent-2);
  color: #b9a8ff;
}
.main {
  background: transparent;
}
</style>
