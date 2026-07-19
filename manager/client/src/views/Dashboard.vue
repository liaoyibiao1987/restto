<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card>
          <div class="stat-num">{{ stats.nodes }}</div>
          <div class="stat-label">节点总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-num">{{ stats.tasks }}</div>
          <div class="stat-label">备份任务</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-num">{{ stats.records }}</div>
          <div class="stat-label">备份记录</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-num">{{ stats.binaries }}</div>
          <div class="stat-label">二进制版本</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue';
import { fetchNodes } from '../api/node';
import { fetchTasks } from '../api/task';
import { fetchRecords } from '../api/record';
import { fetchBinaries } from '../api/binary';

const stats = reactive({ nodes: 0, tasks: 0, records: 0, binaries: 0 });

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
.stat-num {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}
.stat-label {
  color: #666;
  margin-top: 4px;
}
</style>
