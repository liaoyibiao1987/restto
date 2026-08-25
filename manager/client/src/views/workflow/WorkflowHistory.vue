<template>
  <div>
    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="执行号" width="90" />
      <el-table-column prop="workflowId" label="工作流" width="100" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="triggerType" label="触发" width="100">
        <template #default="{ row }">
          <span class="tech-mono">{{ row.triggerType }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="startAt" label="开始" width="180" />
      <el-table-column prop="endAt" label="结束" width="180" />
      <el-table-column prop="errorMsg" label="失败原因" show-overflow-tooltip />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button text type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="pager"
      layout="prev, pager, next, total"
      :total="total"
      :page-size="size"
      :current-page="page"
      @current-change="onPage"
    />

    <el-dialog v-model="detailVisible" title="执行详情" width="720px">
      <el-descriptions :column="2" border v-if="detail">
        <el-descriptions-item label="执行号">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="工作流">{{ detail.workflowName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="触发">{{ detail.triggerType }}</el-descriptions-item>
        <el-descriptions-item label="开始">{{ detail.startAt }}</el-descriptions-item>
        <el-descriptions-item label="结束">{{ detail.endAt }}</el-descriptions-item>
        <el-descriptions-item label="失败原因" :span="2">{{ detail.errorMsg || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail ? detail.nodes : []" border class="node-table">
        <el-table-column prop="nodeKey" label="节点" width="120" />
        <el-table-column prop="label" label="名称" width="140" />
        <el-table-column prop="taskId" label="任务ID" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="原因" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { fetchExecutions, getExecution } from '../../api/workflow';

const route = useRoute();
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const detailVisible = ref(false);
const detail = ref(null);

/**
 * 加载执行历史。
 */
async function load() {
  loading.value = true;
  try {
    const wfId = route.query.workflowId ? Number(route.query.workflowId) : null;
    const data = await fetchExecutions(wfId, page.value, size.value);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } finally {
    loading.value = false;
  }
}

function onPage(p) {
  page.value = p;
  load();
}

/**
 * 打开执行详情。
 *
 * @param {object} row 行数据
 */
async function openDetail(row) {
  detail.value = await getExecution(row.id);
  detailVisible.value = true;
}

/**
 * @param {string} s 状态
 * @returns {string} tag 类型
 */
function statusType(s) {
  if (s === 'success') return 'success';
  if (s === 'failed') return 'danger';
  if (s === 'running') return 'warning';
  if (s === 'skipped') return 'info';
  return 'info';
}

/**
 * @param {string} s 状态
 * @returns {string} 中文标签
 */
function statusLabel(s) {
  const map = {
    running: '运行中',
    success: '成功',
    failed: '失败',
    waiting: '等待',
    skipped: '跳过',
  };
  return map[s] || s;
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  text-align: right;
}
.node-table {
  margin-top: 16px;
}
.tech-mono {
  font-family: var(--tech-font-mono);
  color: var(--tech-accent);
}
</style>
