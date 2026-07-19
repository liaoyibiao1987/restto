<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建节点</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="nodeName" label="节点名" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'online' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="120" />
      <el-table-column prop="lastHeartbeatAt" label="最近心跳" width="200" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
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

    <el-dialog v-model="dialogVisible" title="新建节点" width="420px">
      <el-form label-width="80px">
        <el-form-item label="节点名">
          <el-input v-model="form.nodeName" />
        </el-form-item>
      </el-form>
      <template v-if="createdToken">
        <el-alert type="warning" :closable="false" show-icon>
          节点 Token（仅显示一次，请妥善保存）：<b>{{ createdToken }}</b>
        </el-alert>
      </template>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="submitting" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createNode, deleteNode, fetchNodes } from '../api/node';

const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);

const dialogVisible = ref(false);
const submitting = ref(false);
const createdToken = ref('');
const form = reactive({ nodeName: '' });

/**
 * 加载节点列表。
 */
async function load() {
  loading.value = true;
  try {
    const data = await fetchNodes(page.value, size.value);
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

function openCreate() {
  form.nodeName = '';
  createdToken.value = '';
  dialogVisible.value = true;
}

/**
 * 创建节点，回显一次性 Token。
 */
async function onCreate() {
  submitting.value = true;
  try {
    const node = await createNode(form.nodeName);
    createdToken.value = node.nodeToken;
    ElMessage.success('创建成功');
    load();
  } finally {
    submitting.value = false;
  }
}

/**
 * 删除节点（二次确认）。
 *
 * @param {object} row 行数据
 */
async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除节点 ${row.nodeName} ?`, '提示', { type: 'warning' });
  await deleteNode(row.id);
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
</style>
