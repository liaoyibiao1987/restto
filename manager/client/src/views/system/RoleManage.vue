<template>
  <div class="page">
    <div class="toolbar tech-card">
      <el-input
        v-model="query.roleName"
        placeholder="角色名"
        style="width: 180px"
        clearable
        @keyup.enter="onSearch"
      />
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button type="primary" v-permission="'system:role:create'" @click="openCreate">新建角色</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border row-key="id" class="tech-card">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="roleCode" label="编码" width="160" />
      <el-table-column prop="roleName" label="名称" width="160" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" />
      <el-table-column label="操作" width="320">
        <template #default="{ row }">
          <el-button text type="primary" v-permission="'system:role:assign-menus'" @click="openMenus(row)">分配菜单</el-button>
          <el-button text type="primary" v-permission="'system:role:assign-perms'" @click="openPerms(row)">分配权限</el-button>
          <el-button text type="primary" v-permission="'system:role:update'" @click="openEdit(row)">编辑</el-button>
          <el-button text type="danger" v-permission="'system:role:delete'" @click="onDelete(row)">删除</el-button>
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

    <!-- 新建/编辑 角色 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑角色' : '新建角色'" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="编码">
          <el-input v-model="form.roleCode" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item v-if="editingId" label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配菜单 -->
    <el-dialog v-model="menusVisible" title="分配菜单" width="460px">
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        node-key="id"
        show-checkbox
        check-strictly
        :props="{ label: 'menuName', children: 'children' }"
        default-expand-all
      />
      <template #footer>
        <el-button @click="menusVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onAssignMenus">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog v-model="permsVisible" title="分配权限" width="560px">
      <el-checkbox-group v-model="selectedPermIds">
        <div v-for="group in permGroups" :key="group.module" class="perm-group">
          <div class="perm-group-title">{{ group.module }}</div>
          <el-checkbox v-for="p in group.items" :key="p.id" :label="p.id">
            {{ p.permissionName }}<span class="perm-code">（{{ p.permissionCode }}）</span>
          </el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permsVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onAssignPerms">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  assignRoleMenus,
  assignRolePermissions,
  createRole,
  deleteRole,
  fetchRoleMenus,
  fetchRolePermissions,
  fetchRoles,
  updateRole,
} from '../../api/role';
import { fetchMenuTree } from '../../api/menu';
import { fetchPermissions } from '../../api/permission';

const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const query = reactive({ roleName: '' });

const formVisible = ref(false);
const submitting = ref(false);
const editingId = ref(null);
const form = reactive({ roleCode: '', roleName: '', status: 1, remark: '' });

const menusVisible = ref(false);
const menuTreeRef = ref(null);
const menuTree = ref([]);
const activeRole = ref(null);

const permsVisible = ref(false);
const allPerms = ref([]);
const selectedPermIds = ref([]);

/** 权限按 module 分组。 */
const permGroups = computed(() => {
  const map = {};
  allPerms.value.forEach((p) => {
    if (!map[p.module]) map[p.module] = { module: p.module, items: [] };
    map[p.module].items.push(p);
  });
  return Object.values(map);
});

/**
 * 加载角色列表。
 */
async function load() {
  loading.value = true;
  try {
    const data = await fetchRoles(page.value, size.value, query.roleName);
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

function openCreate() {
  editingId.value = null;
  Object.assign(form, { roleCode: '', roleName: '', status: 1, remark: '' });
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
    roleCode: row.roleCode,
    roleName: row.roleName,
    status: row.status,
    remark: row.remark,
  });
  formVisible.value = true;
}

async function onSave() {
  submitting.value = true;
  try {
    if (editingId.value) {
      await updateRole(editingId.value, {
        roleName: form.roleName,
        status: form.status,
        remark: form.remark,
      });
    } else {
      await createRole({ roleCode: form.roleCode, roleName: form.roleName, remark: form.remark });
    }
    ElMessage.success('已保存');
    formVisible.value = false;
    load();
  } finally {
    submitting.value = false;
  }
}

/**
 * 打开分配菜单。
 *
 * @param {object} row 行数据
 */
async function openMenus(row) {
  activeRole.value = row;
  if (!menuTree.value.length) {
    menuTree.value = await fetchMenuTree();
  }
  const checked = await fetchRoleMenus(row.id);
  menusVisible.value = true;
  // 等待树渲染后回填勾选
  setTimeout(() => {
    menuTreeRef.value && menuTreeRef.value.setCheckedKeys(checked || []);
  }, 0);
}

async function onAssignMenus() {
  submitting.value = true;
  try {
    const keys = menuTreeRef.value.getCheckedKeys();
    await assignRoleMenus(activeRole.value.id, keys);
    ElMessage.success('已分配');
    menusVisible.value = false;
  } finally {
    submitting.value = false;
  }
}

/**
 * 打开分配权限。
 *
 * @param {object} row 行数据
 */
async function openPerms(row) {
  activeRole.value = row;
  if (!allPerms.value.length) {
    const data = await fetchPermissions(1, 1000);
    allPerms.value = data.records || [];
  }
  const checked = await fetchRolePermissions(row.id);
  selectedPermIds.value = [...(checked || [])];
  permsVisible.value = true;
}

async function onAssignPerms() {
  submitting.value = true;
  try {
    await assignRolePermissions(activeRole.value.id, selectedPermIds.value);
    ElMessage.success('已分配');
    permsVisible.value = false;
  } finally {
    submitting.value = false;
  }
}

/**
 * 删除角色。
 *
 * @param {object} row 行数据
 */
async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除角色 ${row.roleName} ?`, '提示', { type: 'warning' });
  await deleteRole(row.id);
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
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
}
.pager {
  margin-top: 4px;
  text-align: right;
}
.perm-group {
  padding: 8px 0;
  border-bottom: 1px dashed var(--tech-border);
}
.perm-group-title {
  color: var(--tech-accent);
  font-family: var(--tech-font-mono);
  margin-bottom: 6px;
}
.perm-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
