<template>
  <div class="page">
    <div class="toolbar tech-card">
      <el-button type="primary" v-permission="'system:menu:create'" @click="openCreate(null)">新增顶级菜单</el-button>
    </div>

    <el-table
      :data="tree"
      v-loading="loading"
      row-key="id"
      border
      :tree-props="{ children: 'children' }"
      default-expand-all
      class="tech-card"
    >
      <el-table-column prop="menuName" label="名称" width="200" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.menuType === 1 ? 'info' : 'success'" size="small">
            {{ row.menuType === 1 ? '目录' : '菜单' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="perms" label="权限码" width="200" />
      <el-table-column prop="path" label="路由 path" width="200" />
      <el-table-column prop="component" label="component" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button text type="primary" v-permission="'system:menu:create'" @click="openCreate(row)">子级</el-button>
          <el-button text type="primary" v-permission="'system:menu:update'" @click="openEdit(row)">编辑</el-button>
          <el-button text type="danger" v-permission="'system:menu:delete'" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑菜单' : '新增菜单'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="父级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'menuName', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="顶级"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.menuName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.menuType">
            <el-radio :label="1">目录</el-radio>
            <el-radio :label="2">菜单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="权限码">
          <el-input v-model="form.perms" placeholder="如 system:user（菜单可空，按钮级别用于按钮控制）" />
        </el-form-item>
        <el-form-item v-if="form.menuType === 2" label="路由 path">
          <el-input v-model="form.path" placeholder="如 /system/user 或 system/user" />
        </el-form-item>
        <el-form-item v-if="form.menuType === 2" label="component">
          <el-input v-model="form.component" placeholder="相对 src/views 的路径，如 system/UserManage" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="是否显示">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createMenu, deleteMenu, fetchMenuTree, updateMenu } from '../../api/menu';

const tree = ref([]);
const loading = ref(false);

const formVisible = ref(false);
const submitting = ref(false);
const editingId = ref(null);
const form = reactive(emptyForm());

/** 父级下拉使用整棵树（可选任意节点为父）。 */
const parentOptions = ref([]);

/**
 * @returns {object} 空表单
 */
function emptyForm() {
  return {
    parentId: null,
    menuName: '',
    menuType: 2,
    perms: '',
    path: '',
    component: '',
    sort: 0,
    visible: 1,
    status: 1,
  };
}

/**
 * 加载菜单树。
 */
async function load() {
  loading.value = true;
  try {
    tree.value = await fetchMenuTree();
    parentOptions.value = tree.value;
  } finally {
    loading.value = false;
  }
}

/**
 * 打开新增。
 *
 * @param {object|null} parent 父级节点（null=顶级）
 */
function openCreate(parent) {
  editingId.value = null;
  Object.assign(form, emptyForm());
  form.parentId = parent ? parent.id : null;
  formVisible.value = true;
}

/**
 * 打开编辑。
 *
 * @param {object} row 行数据
 */
function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, {
    parentId: row.parentId || null,
    menuName: row.menuName,
    menuType: row.menuType,
    perms: row.perms || '',
    path: row.path || '',
    component: row.component || '',
    sort: row.sort || 0,
    visible: row.visible,
    status: row.status,
  });
  formVisible.value = true;
}

async function onSave() {
  submitting.value = true;
  try {
    const payload = { ...form };
    if (editingId.value) {
      await updateMenu(editingId.value, payload);
    } else {
      await createMenu(payload);
    }
    ElMessage.success('已保存');
    formVisible.value = false;
    load();
  } finally {
    submitting.value = false;
  }
}

/**
 * 删除菜单（后端级联子菜单）。
 *
 * @param {object} row 行数据
 */
async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除菜单 ${row.menuName}（含其子菜单）?`, '提示', { type: 'warning' });
  await deleteMenu(row.id);
  ElMessage.success('已删除');
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
  padding: 12px;
}
</style>
