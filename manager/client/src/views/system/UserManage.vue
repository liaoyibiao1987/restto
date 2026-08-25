<template>
  <div class="page">
    <div class="toolbar tech-card">
      <el-input
        v-model="query.username"
        placeholder="用户名"
        style="width: 180px"
        clearable
        @keyup.enter="onSearch"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button type="primary" v-permission="'system:user:create'" @click="openCreate">新建用户</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border row-key="id" class="tech-card">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="nickname" label="昵称" width="140" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column label="角色" width="180">
        <template #default="{ row }">
          <el-tag v-for="c in row.roleCodes" :key="c" size="small" class="role-tag">{{ c }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button text type="primary" v-permission="'system:user:assign-roles'" @click="openRoles(row)">分配角色</el-button>
          <el-button text type="primary" v-permission="'system:user:reset-password'" @click="openReset(row)">重置密码</el-button>
          <el-button text type="danger" v-permission="'system:user:delete'" @click="onDelete(row)">删除</el-button>
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

    <!-- 新建/编辑 用户 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑用户' : '新建用户'" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item v-if="editingId" label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 -->
    <el-dialog v-model="rolesVisible" title="分配角色" width="420px">
      <el-checkbox-group v-model="selectedRoleIds">
        <div v-for="r in allRoles" :key="r.id" class="role-line">
          <el-checkbox :label="r.id">{{ r.roleName }}（{{ r.roleCode }}）</el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="rolesVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onAssignRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetVisible" title="重置密码" width="400px">
      <el-form label-width="80px">
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onReset">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  assignUserRoles,
  createUser,
  deleteUser,
  fetchUsers,
  resetUserPassword,
  updateUser,
} from '../../api/user';
import { fetchAllRoles } from '../../api/role';

const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const query = reactive({ username: '', status: undefined });

const formVisible = ref(false);
const submitting = ref(false);
const editingId = ref(null);
const form = reactive({ username: '', password: '', nickname: '', email: '', status: 1 });

const rolesVisible = ref(false);
const allRoles = ref([]);
const selectedRoleIds = ref([]);
const activeUser = ref(null);

const resetVisible = ref(false);
const newPassword = ref('');

/**
 * 加载用户列表。
 */
async function load() {
  loading.value = true;
  try {
    const data = await fetchUsers(page.value, size.value, query.username, query.status);
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
  Object.assign(form, { username: '', password: '', nickname: '', email: '', status: 1 });
  formVisible.value = true;
}

/**
 * 保存（新建或编辑）。
 */
async function onSave() {
  submitting.value = true;
  try {
    if (editingId.value) {
      await updateUser(editingId.value, {
        nickname: form.nickname,
        email: form.email,
        status: form.status,
      });
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        nickname: form.nickname,
        email: form.email,
      });
    }
    ElMessage.success('已保存');
    formVisible.value = false;
    load();
  } finally {
    submitting.value = false;
  }
}

/**
 * 打开分配角色。
 *
 * @param {object} row 行数据
 */
async function openRoles(row) {
  activeUser.value = row;
  selectedRoleIds.value = [...(row.roleIds || [])];
  if (!allRoles.value.length) {
    allRoles.value = await fetchAllRoles();
  }
  rolesVisible.value = true;
}

async function onAssignRoles() {
  submitting.value = true;
  try {
    await assignUserRoles(activeUser.value.id, selectedRoleIds.value);
    ElMessage.success('已分配');
    rolesVisible.value = false;
    load();
  } finally {
    submitting.value = false;
  }
}

function openReset(row) {
  activeUser.value = row;
  newPassword.value = '';
  resetVisible.value = true;
}

async function onReset() {
  submitting.value = true;
  try {
    await resetUserPassword(activeUser.value.id, newPassword.value);
    ElMessage.success('已重置');
    resetVisible.value = false;
  } finally {
    submitting.value = false;
  }
}

/**
 * 删除用户。
 *
 * @param {object} row 行数据
 */
async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除用户 ${row.username} ?`, '提示', { type: 'warning' });
  await deleteUser(row.id);
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
.role-tag {
  margin-right: 4px;
}
.role-line {
  padding: 4px 0;
}
.pager {
  margin-top: 4px;
  text-align: right;
}
</style>
