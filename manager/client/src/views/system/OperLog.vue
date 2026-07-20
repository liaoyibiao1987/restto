<template>
  <div class="page">
    <div class="toolbar tech-card">
      <el-input v-model="query.title" placeholder="操作" style="width: 160px" clearable @keyup.enter="onSearch" />
      <el-input v-model="query.operUser" placeholder="操作人" style="width: 140px" clearable @keyup.enter="onSearch" />
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button type="danger" v-permission="'system:log:delete'" @click="onClear">清空全部</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border class="tech-card">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="操作" width="160" />
      <el-table-column prop="operUser" label="操作人" width="120" />
      <el-table-column prop="requestMethod" label="方式" width="80" />
      <el-table-column prop="operUri" label="URI" />
      <el-table-column prop="operIp" label="IP" width="130" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="costMs" label="耗时(ms)" width="100" />
      <el-table-column prop="operTime" label="时间" width="180" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button text type="primary" v-permission="'system:log:query'" @click="openDetail(row)">详情</el-button>
          <el-button text type="danger" v-permission="'system:log:delete'" @click="onDelete(row)">删除</el-button>
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

    <el-drawer v-model="detailVisible" title="操作日志详情" size="520px">
      <div v-if="detail" class="detail">
        <div class="detail-row"><span class="k">操作</span><span>{{ detail.title }}</span></div>
        <div class="detail-row"><span class="k">操作人</span><span>{{ detail.operUser }}</span></div>
        <div class="detail-row"><span class="k">方法</span><span class="tech-mono">{{ detail.operMethod }}</span></div>
        <div class="detail-row"><span class="k">URI</span><span class="tech-mono">{{ detail.operUri }}</span></div>
        <div class="detail-row"><span class="k">耗时</span><span class="tech-mono">{{ detail.costMs }} ms</span></div>
        <div class="detail-row"><span class="k">时间</span><span>{{ detail.operTime }}</span></div>
        <div v-if="detail.errorMsg" class="detail-row">
          <span class="k">错误</span>
          <pre class="json error">{{ detail.errorMsg }}</pre>
        </div>
        <div class="detail-row">
          <span class="k">入参</span>
          <pre class="json">{{ prettyParams }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { clearLogs, deleteLog, fetchLogs } from '../../api/log';

const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const query = reactive({ title: '', operUser: '' });

const detailVisible = ref(false);
const detail = ref(null);

/** 入参 JSON 美化（失败则原样）。 */
const prettyParams = computed(() => {
  if (!detail.value || !detail.value.requestParams) return '(无)';
  try {
    return JSON.stringify(JSON.parse(detail.value.requestParams), null, 2);
  } catch (e) {
    return detail.value.requestParams;
  }
});

/**
 * 加载日志列表。
 */
async function load() {
  loading.value = true;
  try {
    const data = await fetchLogs(page.value, size.value, query.title, query.operUser);
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
 * 打开详情。
 *
 * @param {object} row 行数据
 */
function openDetail(row) {
  detail.value = row;
  detailVisible.value = true;
}

/**
 * 删除单条。
 *
 * @param {object} row 行数据
 */
async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除该日志?`, '提示', { type: 'warning' });
  await deleteLog(row.id);
  ElMessage.success('已删除');
  load();
}

async function onClear() {
  await ElMessageBox.confirm('确认清空全部操作日志? 此操作不可恢复。', '危险操作', { type: 'error' });
  await clearLogs();
  ElMessage.success('已清空');
  load();
}

onMounted(load);
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
}
.pager {
  margin-top: 4px;
  text-align: right;
}
.detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.k {
  color: var(--tech-accent);
  font-size: 12px;
  letter-spacing: 1px;
}
.json {
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid var(--tech-border);
  border-radius: 6px;
  padding: 10px;
  color: var(--el-text-color-regular);
  font-family: var(--tech-font-mono);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 280px;
  overflow: auto;
}
.json.error {
  color: var(--tech-danger);
  border-color: var(--tech-danger);
}
</style>
