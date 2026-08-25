<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col v-for="card in cards" :key="card.key" :span="6">
        <div class="stat-card tech-card">
          <div class="stat-icon" :style="{ color: card.color, borderColor: card.color }">
            {{ card.icon }}
          </div>
          <div class="stat-body">
            <div class="stat-num tech-mono">{{ stats[card.key] }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>
    <div class="panel tech-card">
      <div class="panel-title">系统状态</div>
      <div class="status-grid">
        <div class="status-item">
          <span class="dot online" /> 节点通信 Netty 9600
        </div>
        <div class="status-item">
          <span class="dot" /> JWT 鉴权 + RBAC 权限校验
        </div>
        <div class="status-item">
          <span class="dot" /> 操作日志 AOP 审计
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue';
import { fetchNodes } from '../api/node';
import { fetchTasks } from '../api/task';
import { fetchRecords } from '../api/record';
import { fetchBinaries } from '../api/binary';

const stats = reactive({ nodes: 0, tasks: 0, records: 0, binaries: 0 });

const cards = [
  { key: 'nodes', label: '节点总数', icon: '⬡', color: '#00e5ff' },
  { key: 'tasks', label: '备份任务', icon: '▤', color: '#7c5cff' },
  { key: 'records', label: '备份记录', icon: '▦', color: '#00ff9d' },
  { key: 'binaries', label: '二进制版本', icon: '◈', color: '#ffb454' },
];

/**
 * 加载各模块总数（取分页 total）。
 */
async function loadStats() {
  const [nodes, tasks, records, binaries] = await Promise.all([
    fetchNodes(1, 1).catch(() => ({ total: 0 })),
    fetchTasks(1, 1).catch(() => ({ total: 0 })),
    fetchRecords(null, 1, 1).catch(() => ({ total: 0 })),
    fetchBinaries(1, 1).catch(() => ({ total: 0 })),
  ]);
  stats.nodes = nodes.total || 0;
  stats.tasks = tasks.total || 0;
  stats.records = records.total || 0;
  stats.binaries = binaries.total || 0;
}

onMounted(loadStats);
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}
.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border: 1px solid;
  border-radius: 10px;
  font-size: 22px;
  text-shadow: var(--tech-glow);
}
.stat-num {
  font-size: 30px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 4px;
}
.panel {
  padding: 20px;
}
.panel-title {
  color: var(--tech-accent);
  letter-spacing: 1px;
  margin-bottom: 16px;
  font-size: 14px;
}
.status-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.status-item {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-text-color-secondary);
}
.dot.online {
  background: var(--tech-success);
  box-shadow: 0 0 8px var(--tech-success);
}
</style>
