<template>
  <div>
    <div class="toolbar">
      <el-upload
        :auto-upload="false"
        :show-file-list="false"
        :on-change="onFileChange"
      >
        <el-button type="primary">选择二进制</el-button>
      </el-upload>
      <el-input v-model="version" placeholder="版本号，如 0.2.0" style="width: 200px" />
      <el-button type="success" :loading="uploading" @click="onUpload">上传</el-button>
      <span v-if="file" class="file-name">{{ file.name }}</span>
    </div>

    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="version" label="版本" width="140" />
      <el-table-column prop="checksum" label="sha256" />
      <el-table-column prop="size" label="大小" width="120" />
      <el-table-column prop="uploadedAt" label="上传时间" width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button text type="primary" @click="openPush(row)">下发</el-button>
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

    <el-dialog v-model="pushVisible" title="下发到节点" width="420px">
      <el-form label-width="80px">
        <el-form-item label="节点">
          <el-select v-model="pushNodeId" placeholder="选择节点">
            <el-option
              v-for="n in nodes"
              :key="n.id"
              :label="`${n.nodeName} (#${n.id})`"
              :value="n.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pushVisible = false">取消</el-button>
        <el-button type="primary" :loading="pushing" @click="onPush">下发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fetchNodes } from '../api/node';
import { fetchBinaries, pushBinary, uploadBinary } from '../api/binary';

const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const nodes = ref([]);

const file = ref(null);
const version = ref('');
const uploading = ref(false);

const pushVisible = ref(false);
const pushNodeId = ref(null);
const pushing = ref(false);
const activeBinaryId = ref(null);

/**
 * 加载二进制版本列表。
 */
async function load() {
  loading.value = true;
  try {
    const data = await fetchBinaries(page.value, size.value);
    rows.value = data.records || [];
    total.value = data.total || 0;
  } finally {
    loading.value = false;
  }
}

async function loadNodes() {
  const data = await fetchNodes(1, 200);
  nodes.value = data.records || [];
}

function onPage(p) {
  page.value = p;
  load();
}

/**
 * 文件选择回调。
 *
 * @param {object} uploadFile Element Plus 上传文件对象
 */
function onFileChange(uploadFile) {
  file.value = uploadFile.raw;
}

/**
 * 上传二进制。
 */
async function onUpload() {
  if (!file.value || !version.value) {
    ElMessage.warning('请选择文件并填写版本号');
    return;
  }
  uploading.value = true;
  try {
    await uploadBinary(file.value, version.value);
    ElMessage.success('上传成功');
    file.value = null;
    version.value = '';
    load();
  } finally {
    uploading.value = false;
  }
}

/**
 * 打开下发对话框。
 *
 * @param {object} row 行数据
 */
function openPush(row) {
  activeBinaryId.value = row.id;
  pushNodeId.value = null;
  pushVisible.value = true;
}

/**
 * 执行下发。
 */
async function onPush() {
  if (!pushNodeId.value) {
    ElMessage.warning('请选择节点');
    return;
  }
  pushing.value = true;
  try {
    const ok = await pushBinary(activeBinaryId.value, pushNodeId.value);
    ElMessage[ok ? 'success' : 'warning'](ok ? '已下发' : '节点离线，未下发');
    pushVisible.value = false;
  } finally {
    pushing.value = false;
  }
}

onMounted(() => {
  loadNodes();
  load();
});
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.file-name {
  color: #666;
}
.pager {
  margin-top: 12px;
  text-align: right;
}
</style>
