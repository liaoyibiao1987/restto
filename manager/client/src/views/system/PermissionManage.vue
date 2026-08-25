<template>
  <div class="page">
    <div class="toolbar tech-card">
      <el-select v-model="query.module" placeholder="模块" clearable style="width: 160px" @change="onSearch">
        <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <span class="tip">权限点为种子驱动（V2 只读），如需调整请在 migrations 新增迁移。</span>
    </div>

    <el-table :data="rows" v-loading="loading" border class="tech-card">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="permissionCode" label="权限码" width="240" />
      <el-table-column prop="permissionName" label="名称" width="160" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="remark" label="备注" />
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
import { onMounted, reactive, ref } from 'vue';
import { fetchPermissions } from '../../api/permission';

const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const query = reactive({ module: '' });
const moduleOptions = ref(['system', 'backup']);

/**
 * 加载权限点列表。
 */
async function load() {
  loading.value = true;
  try {
    const data = await fetchPermissions(page.value, size.value, query.module);
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
.tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.pager {
  margin-top: 4px;
  text-align: right;
}
</style>
