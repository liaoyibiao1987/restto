<template>
  <div>
    <div class="toolbar">
      <el-button
        v-permission="'workflow:workflow:create'"
        type="primary"
        @click="onCreate"
      >新建工作流</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="工作流名" />
      <el-table-column prop="cronExpr" label="cron" width="180">
        <template #default="{ row }">
          <span class="tech-mono">{{ row.cronExpr || '仅手动' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="300">
        <template #default="{ row }">
          <el-button text type="primary" @click="onDesign(row)">设计</el-button>
          <el-button
            v-permission="'workflow:workflow:run'"
            text
            type="success"
            @click="onRun(row)"
          >运行</el-button>
          <el-button text @click="onHistory(row)">历史</el-button>
          <el-button
            v-permission="'workflow:workflow:delete'"
            text
            type="danger"
            @click="onDelete(row)"
          >删除</el-button>
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  deleteWorkflow,
  fetchWorkflows,
  runWorkflow,
} from '../../api/workflow';

const router = useRouter();
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);

/**
 * 加载工作流列表。
 */
async function load() {
  loading.value = true;
  try {
    const data = await fetchWorkflows(page.value, size.value);
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

function onCreate() {
  router.push('/workflow/design');
}

/**
 * 打开设计器。
 *
 * @param {object} row 行数据
 */
function onDesign(row) {
  router.push({ path: '/workflow/design', query: { id: row.id } });
}

/**
 * 跳转执行历史。
 *
 * @param {object} row 行数据
 */
function onHistory(row) {
  router.push({ path: '/workflow/history', query: { workflowId: row.id } });
}

/**
 * 运行工作流。
 *
 * @param {object} row 行数据
 */
async function onRun(row) {
  const execId = await runWorkflow(row.id);
  ElMessage.success(`已触发，执行号 #${execId}`);
}

/**
 * 删除工作流。
 *
 * @param {object} row 行数据
 */
async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除工作流 ${row.name} ？执行历史将保留。`, '提示', {
    type: 'warning',
  });
  await deleteWorkflow(row.id);
  ElMessage.success('已删除');
  load();
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
.tech-mono {
  font-family: var(--tech-font-mono);
  color: var(--tech-accent);
}
</style>
