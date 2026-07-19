<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="taskId"
        placeholder="按任务ID过滤（可空）"
        style="width: 220px"
        clearable
        @keyup.enter="onSearch"
      />
      <el-button type="primary" @click="onSearch">查询</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="taskId" label="任务ID" width="90" />
      <el-table-column prop="nodeId" label="节点ID" width="90" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="filePath" label="产物路径" />
      <el-table-column prop="size" label="大小" width="120" />
      <el-table-column prop="startAt" label="开始" width="180" />
      <el-table-column prop="endAt" label="结束" width="180" />
      <el-table-column prop="errorMsg" label="错误" />
    </el-table>
    <el-pagination
      class="pager"
      layout="prev, pager, next, total"
      :total="total"
      :page-size="size"
      :current-page="page"
      @current-change="onPage"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { fetchRecords } from '../api/record';

const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const taskId = ref('');

/**
 * 加载记录。
 */
async function load() {
  loading.value = true;
  try {
    const id = taskId.value ? Number(taskId.value) : null;
    const data = await fetchRecords(id, page.value, size.value);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  page.value = 1;
  load();
}

function onPage(p) {
  page.value = p;
  load();
}

/**
 * 状态 -> tag 类型。
 *
 * @param {string} status 状态
 * @returns {string} tag type
 */
function statusType(status) {
  if (status === 'success') return 'success';
  if (status === 'failed') return 'danger';
  return 'info';
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  text-align: right;
}
</style>
