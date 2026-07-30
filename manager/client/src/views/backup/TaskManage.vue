<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建任务</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="任务名" />
      <el-table-column prop="nodeId" label="节点ID" width="90" />
      <el-table-column prop="module" label="模块" width="140" />
      <el-table-column prop="cronExpr" label="cron" width="160" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button text type="primary" @click="onRun(row)">执行</el-button>
          <el-button text @click="openEdit(row)">编辑</el-button>
          <el-button text type="danger" @click="onDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑任务' : '新建任务'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="任务名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="目标节点">
          <el-select v-model="form.nodeId" placeholder="选择节点">
            <el-option
              v-for="n in nodes"
              :key="n.id"
              :label="`${n.nodeName} (#${n.id})`"
              :value="n.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="form.module">
            <el-option label="backup_file" value="backup_file" />
            <el-option label="backup_mysql" value="backup_mysql" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数(JSON)">
          <el-input v-model="form.paramsJson" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="cron 表达式">
          <el-input v-model="form.cronExpr" placeholder="如 0 30 2 * * *（每天 02:30）" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fetchNodes } from '../../api/node';
import {
  createTask,
  deleteTask,
  fetchTasks,
  runTask,
  updateTask,
} from '../../api/task';

const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const nodes = ref([]);

const dialogVisible = ref(false);
const submitting = ref(false);
const editingId = ref(null);
const form = reactive(emptyForm());

/**
 * @returns {object} 空表单
 */
function emptyForm() {
  return {
    name: '',
    nodeId: null,
    module: 'backup_file',
    paramsJson: '{"path":"/data","dest":"/backup/data.tar.gz"}',
    cronExpr: '',
    enabled: true,
  };
}

/**
 * 加载任务列表。
 */
async function load() {
  loading.value = true;
  try {
    const data = await fetchTasks(page.value, size.value);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } finally {
    loading.value = false;
  }
}

/**
 * 加载节点下拉。
 */
async function loadNodes() {
  const data = await fetchNodes(1, 200);
  nodes.value = data.records || [];
}

function onPage(p) {
  page.value = p;
  load();
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, emptyForm());
  dialogVisible.value = true;
}

/**
 * 打开编辑。
 *
 * @param {object} row 行数据
 */
function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, {
    name: row.name,
    nodeId: row.nodeId,
    module: row.module,
    paramsJson: row.paramsJson || '',
    cronExpr: row.cronExpr || '',
    enabled: row.enabled,
  });
  dialogVisible.value = true;
}

/**
 * 保存（创建或更新）。
 */
async function onSave() {
  submitting.value = true;
  try {
    if (editingId.value) {
      await updateTask(editingId.value, { ...form });
    } else {
      await createTask({ ...form });
    }
    ElMessage.success('已保存');
    dialogVisible.value = false;
    load();
  } finally {
    submitting.value = false;
  }
}

/**
 * 立即执行任务。
 *
 * @param {object} row 行数据
 */
async function onRun(row) {
  const ok = await runTask(row.id);
  ElMessage[ok ? 'success' : 'warning'](ok ? '已下发' : '节点离线，未下发');
}

/**
 * 删除任务。
 *
 * @param {object} row 行数据
 */
async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除任务 ${row.name} ?`, '提示', { type: 'warning' });
  await deleteTask(row.id);
  ElMessage.success('已删除');
  load();
}

onMounted(() => {
  loadNodes();
  load();
});
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  text-align: right;
}
</style>
