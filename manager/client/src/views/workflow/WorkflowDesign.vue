<template>
  <div class="wf-design">
    <!-- 工具栏 -->
    <div class="wf-toolbar tech-card">
      <el-button @click="onBack">← 返回</el-button>
      <el-input v-model="form.name" placeholder="工作流名称" class="name-input" />
      <el-input v-model="form.cronExpr" placeholder="cron（可空，仅手动）" class="cron-input" />
      <el-switch v-model="form.enabled" active-text="启用" />
      <span class="spacer" />
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      <el-button type="success" :disabled="!editingId" @click="onRun">运行</el-button>
    </div>

    <div class="wf-body">
      <!-- 任务面板 -->
      <div class="wf-palette tech-card">
        <div class="palette-title">任务面板</div>
        <div class="palette-hint">点击添加节点</div>
        <div class="palette-list">
          <div
            v-for="t in tasks"
            :key="t.id"
            class="palette-item"
            @click="addNode(t)"
          >
            <span class="pi-name">{{ t.name }}</span>
            <span class="pi-module">{{ t.module }}</span>
          </div>
          <div v-if="!tasks.length" class="palette-empty">暂无任务</div>
        </div>
      </div>

      <!-- 画布 -->
      <div ref="canvasWrap" class="wf-canvas">
        <canvas ref="bgCanvas" class="bg-canvas" />
        <!-- 连线层 -->
        <svg class="edge-layer">
          <defs>
            <filter id="edge-glow" x="-50%" y="-50%" width="200%" height="200%">
              <feGaussianBlur stdDeviation="2.5" result="b" />
              <feMerge>
                <feMergeNode in="b" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
            <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto">
              <path d="M0,0 L8,3 L0,6 Z" :fill="edgeColor('always')" />
            </marker>
          </defs>
          <path
            v-for="e in edgePaths"
            :key="`${e.sourceKey}->${e.targetKey}`"
            :d="e.d"
            :stroke="edgeColor(e.condition)"
            :marker-end="`url(#arrow)`"
            class="edge-line"
            filter="url(#edge-glow)"
            @click="cycleEdge(e)"
          />
          <text
            v-for="e in edgePaths"
            :key="`t-${e.sourceKey}->${e.targetKey}`"
            :x="e.midX"
            :y="e.midY"
            class="edge-label"
            @click="cycleEdge(e)"
          >{{ edgeLabel(e.condition) }}</text>
          <!-- 拖拽中的临时连线 -->
          <path v-if="dragEdge" :d="dragEdgePath" stroke="#00e5ff" class="edge-draft" />
        </svg>

        <!-- 节点卡片层 -->
        <div
          v-for="n in nodes"
          :key="n.nodeKey"
          class="wf-node tech-card"
          :class="{ selected: n.nodeKey === selectedKey }"
          :style="nodeStyle(n)"
          @pointerenter="hoverKey = n.nodeKey"
          @pointerleave="hoverKey = null"
          @click.stop="selectNode(n.nodeKey)"
        >
          <div class="node-head" @pointerdown="startDrag($event, n)">
            <span class="node-title">{{ n.label || n.nodeKey }}</span>
            <span class="node-del" @click.stop="removeNode(n)">✕</span>
          </div>
          <div class="node-body">
            <div class="node-meta">task #{{ n.taskId }}</div>
          </div>
          <!-- 出端口（拖出连线） -->
          <div
            class="port port-out"
            @pointerdown.stop="startEdge($event, n)"
            title="拖到目标节点连线"
          >→</div>
          <!-- 入端口（视觉提示，命中由卡片 hover 判定） -->
          <div class="port port-in">←</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import * as THREE from 'three';
import { fetchTasks } from '../../api/task';
import {
  createWorkflow,
  getWorkflow,
  runWorkflow,
  updateWorkflow,
} from '../../api/workflow';

const route = useRoute();
const router = useRouter();

const editingId = ref(route.query.id ? Number(route.query.id) : null);
const tasks = ref([]);
const nodes = ref([]);
const edges = ref([]);
const form = reactive({ name: '', cronExpr: '', enabled: true });
const saving = ref(false);

const selectedKey = ref(null);
const hoverKey = ref(null);
const dragEdge = ref(null); // { sourceKey, x, y }

const canvasWrap = ref(null);
const bgCanvas = ref(null);

let renderer = null;
let scene = null;
let camera = null;
let frameId = 0;
let points = null;

/** 节点尺寸（与端口坐标计算一致）。 */
const NODE_W = 190;
const NODE_H = 78;
const GRID = 20;
let keySeq = 0;

/**
 * 生成唯一节点键。
 *
 * @returns {string}
 */
function nextKey() {
  keySeq += 1;
  return `n${Date.now().toString(36)}${keySeq}`;
}

/**
 * 加载工作流详情。
 */
async function loadWorkflow() {
  if (!editingId.value) return;
  const data = await getWorkflow(editingId.value);
  form.name = data.name || '';
  form.cronExpr = data.cronExpr || '';
  form.enabled = data.enabled !== false;
  nodes.value = (data.nodes || []).map((n) => ({
    nodeKey: n.nodeKey,
    label: n.label,
    taskId: n.taskId,
    posX: n.posX || 0,
    posY: n.posY || 0,
  }));
  edges.value = (data.edges || []).map((e) => ({
    sourceKey: e.sourceKey,
    targetKey: e.targetKey,
    condition: e.condition || 'always',
  }));
}

/**
 * 添加节点（从任务面板）。
 *
 * @param {object} task 任务
 */
function addNode(task) {
  const cx = canvasWrap.value ? canvasWrap.value.clientWidth / 2 : 400;
  const cy = canvasWrap.value ? canvasWrap.value.clientHeight / 2 : 200;
  const offset = (nodes.value.length % 6) * 30;
  nodes.value.push({
    nodeKey: nextKey(),
    label: task.name,
    taskId: task.id,
    posX: snap(cx - NODE_W / 2 + offset - 120),
    posY: snap(cy - NODE_H / 2 + offset - 90),
  });
}

/**
 * @param {number} v 坐标
 * @returns {number} 吸附到网格
 */
function snap(v) {
  return Math.round(v / GRID) * GRID;
}

/**
 * @param {object} n 节点
 * @returns {object} 样式
 */
function nodeStyle(n) {
  return { left: `${n.posX}px`, top: `${n.posY}px`, width: `${NODE_W}px` };
}

/**
 * 计算节点端口坐标（画布坐标系）。
 *
 * @param {object} n 节点
 * @returns {{outX:number,outY:number,inX:number,inY:number}}
 */
function ports(n) {
  return {
    outX: n.posX + NODE_W,
    outY: n.posY + 28,
    inX: n.posX,
    inY: n.posY + 28,
  };
}

/**
 * 由两点构造水平贝塞尔路径。
 *
 * @param {number} x1 起 x
 * @param {number} y1 起 y
 * @param {number} x2 终 x
 * @param {number} y2 终 y
 * @returns {string} svg d
 */
function bezier(x1, y1, x2, y2) {
  const dx = Math.max(40, Math.abs(x2 - x1) * 0.5);
  return `M${x1},${y1} C${x1 + dx},${y1} ${x2 - dx},${y2} ${x2},${y2}`;
}

/** 连线路径（computed）。 */
const edgePaths = computed(() => {
  const byKey = {};
  nodes.value.forEach((n) => {
    byKey[n.nodeKey] = n;
  });
  return edges.value
    .map((e) => {
      const s = byKey[e.sourceKey];
      const t = byKey[e.targetKey];
      if (!s || !t) return null;
      const sp = ports(s);
      const tp = ports(t);
      const d = bezier(sp.outX, sp.outY, tp.inX, tp.inY);
      return {
        ...e,
        d,
        midX: (sp.outX + tp.inX) / 2,
        midY: (sp.outY + tp.inY) / 2,
      };
    })
    .filter(Boolean);
});

/** 拖拽中临时连线。 */
const dragEdgePath = computed(() => {
  if (!dragEdge.value) return '';
  const s = nodes.value.find((n) => n.nodeKey === dragEdge.value.sourceKey);
  if (!s) return '';
  const sp = ports(s);
  return bezier(sp.outX, sp.outY, dragEdge.value.x, dragEdge.value.y);
});

/**
 * @param {string} cond 条件
 * @returns {string} 颜色
 */
function edgeColor(cond) {
  if (cond === 'on_success') return '#00ff9d';
  if (cond === 'on_failed') return '#ff4d6d';
  return '#00e5ff';
}

/**
 * @param {string} cond 条件
 * @returns {string} 标签
 */
function edgeLabel(cond) {
  if (cond === 'on_success') return '成功';
  if (cond === 'on_failed') return '失败';
  return '总是';
}

/**
 * 点击边循环切换条件。
 *
 * @param {object} e 边
 */
function cycleEdge(e) {
  const order = ['always', 'on_success', 'on_failed'];
  const idx = order.indexOf(e.condition || 'always');
  e.condition = order[(idx + 1) % order.length];
}

/**
 * 拖拽节点。
 *
 * @param {PointerEvent} ev 事件
 * @param {object} n 节点
 */
function startDrag(ev, n) {
  ev.preventDefault();
  const rect = canvasWrap.value.getBoundingClientRect();
  const startX = ev.clientX;
  const startY = ev.clientY;
  const origX = n.posX;
  const origY = n.posY;
  selectedKey.value = n.nodeKey;

  /**
   * @param {PointerEvent} e 移动事件
   */
  function onMove(e) {
    n.posX = origX + (e.clientX - startX);
    n.posY = origY + (e.clientY - startY);
  }
  /**
   * @param {PointerEvent} e 抬起事件
   */
  function onUp() {
    n.posX = snap(n.posX);
    n.posY = snap(n.posY);
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onUp);
  }
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp);
}

/**
 * 从端口拖出连线。
 *
 * @param {PointerEvent} ev 事件
 * @param {object} n 源节点
 */
function startEdge(ev, n) {
  ev.preventDefault();
  const rect = canvasWrap.value.getBoundingClientRect();

  /**
   * @param {PointerEvent} e 移动事件
   */
  function onMove(e) {
    dragEdge.value = {
      sourceKey: n.nodeKey,
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    };
  }
  /**
   * @param {PointerEvent} e 抬起事件
   */
  function onUp(e) {
    const targetKey = hoverKey.value;
    if (targetKey && targetKey !== n.nodeKey) {
      const exists = edges.value.some(
        (ed) => ed.sourceKey === n.nodeKey && ed.targetKey === targetKey,
      );
      if (!exists) {
        edges.value.push({
          sourceKey: n.nodeKey,
          targetKey,
          condition: 'always',
        });
      }
    }
    dragEdge.value = null;
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onUp);
  }
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp);
}

/**
 * 选中节点。
 *
 * @param {string} key 节点键
 */
function selectNode(key) {
  selectedKey.value = key;
}

/**
 * 删除节点（及其关联边）。
 *
 * @param {object} n 节点
 */
function removeNode(n) {
  nodes.value = nodes.value.filter((x) => x.nodeKey !== n.nodeKey);
  edges.value = edges.value.filter(
    (e) => e.sourceKey !== n.nodeKey && e.targetKey !== n.nodeKey,
  );
  if (selectedKey.value === n.nodeKey) selectedKey.value = null;
}

/**
 * 保存工作流（新增或整图替换）。
 */
async function onSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写工作流名称');
    return;
  }
  if (!nodes.value.length) {
    ElMessage.warning('至少添加一个节点');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      name: form.name,
      cronExpr: form.cronExpr || null,
      enabled: form.enabled,
      nodes: nodes.value.map((n) => ({
        nodeKey: n.nodeKey,
        label: n.label,
        taskId: n.taskId,
        posX: n.posX,
        posY: n.posY,
      })),
      edges: edges.value.map((e) => ({
        sourceKey: e.sourceKey,
        targetKey: e.targetKey,
        condition: e.condition,
      })),
    };
    let data;
    if (editingId.value) {
      data = await updateWorkflow(editingId.value, payload);
    } else {
      data = await createWorkflow(payload);
      editingId.value = data.id;
    }
    ElMessage.success('已保存');
  } finally {
    saving.value = false;
  }
}

/**
 * 运行工作流（需先保存）。
 */
async function onRun() {
  if (!editingId.value) {
    ElMessage.warning('请先保存');
    return;
  }
  const execId = await runWorkflow(editingId.value);
  ElMessage.success(`已触发，执行号 #${execId}`);
}

function onBack() {
  router.push('/backup/workflow');
}

// ===== three.js 深空背景 =====

/**
 * 初始化 three.js 背景场景。
 */
function initThree() {
  const wrap = canvasWrap.value;
  const canvas = bgCanvas.value;
  if (!wrap || !canvas) return;
  const w = wrap.clientWidth;
  const h = wrap.clientHeight;

  renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(w, h);

  scene = new THREE.Scene();
  camera = new THREE.PerspectiveCamera(55, w / h, 1, 4000);
  camera.position.set(0, 520, 820);
  camera.lookAt(0, 0, 0);

  // 地面网格（青）
  const grid = new THREE.GridHelper(3000, 60, 0x00e5ff, 0x123a4a);
  grid.material.transparent = true;
  grid.material.opacity = 0.35;
  scene.add(grid);

  // 粒子场（加色混合）
  const count = 900;
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count; i += 1) {
    positions[i * 3] = (Math.random() - 0.5) * 3000;
    positions[i * 3 + 1] = Math.random() * 700;
    positions[i * 3 + 2] = (Math.random() - 0.5) * 3000;
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  const mat = new THREE.PointsMaterial({
    color: 0x7c5cff,
    size: 4,
    transparent: true,
    opacity: 0.7,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  points = new THREE.Points(geo, mat);
  scene.add(points);

  animate();
}

let t0 = 0;

/**
 * 动画循环。
 */
function animate() {
  frameId = requestAnimationFrame(animate);
  t0 += 0.0035;
  if (points) {
    points.rotation.y += 0.0008;
    points.position.y = Math.sin(t0) * 12;
  }
  if (camera) {
    camera.position.x = Math.sin(t0 * 0.5) * 60;
    camera.lookAt(0, 0, 0);
  }
  if (renderer && scene && camera) {
    renderer.render(scene, camera);
  }
}

/**
 * 窗口尺寸变化。
 */
function onResize() {
  if (!renderer || !camera || !canvasWrap.value) return;
  const w = canvasWrap.value.clientWidth;
  const h = canvasWrap.value.clientHeight;
  renderer.setSize(w, h);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
}

onMounted(async () => {
  initThree();
  window.addEventListener('resize', onResize);
  const data = await fetchTasks(1, 200);
  tasks.value = data.records || [];
  await loadWorkflow();
});

onUnmounted(() => {
  window.removeEventListener('resize', onResize);
  if (frameId) cancelAnimationFrame(frameId);
  if (renderer) renderer.dispose();
});
</script>

<style scoped>
.wf-design {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 90px);
}
.wf-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 10px;
}
.name-input {
  width: 220px;
}
.cron-input {
  width: 220px;
}
.spacer {
  flex: 1;
}
.wf-body {
  flex: 1;
  display: flex;
  gap: 10px;
  min-height: 0;
}
.wf-palette {
  width: 220px;
  padding: 12px;
  overflow-y: auto;
}
.palette-title {
  font-family: var(--tech-font-mono);
  color: var(--tech-accent);
  letter-spacing: 1px;
  margin-bottom: 4px;
}
.palette-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}
.palette-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.palette-item {
  padding: 8px 10px;
  border: 1px solid var(--tech-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.18s;
  background: rgba(0, 229, 255, 0.04);
}
.palette-item:hover {
  border-color: var(--tech-accent);
  box-shadow: var(--tech-glow);
  transform: translateX(2px);
}
.pi-name {
  display: block;
  color: var(--el-text-color-primary);
}
.pi-module {
  font-family: var(--tech-font-mono);
  font-size: 11px;
  color: var(--tech-accent-2);
}
.palette-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.wf-canvas {
  position: relative;
  flex: 1;
  overflow: hidden;
  border: 1px solid var(--tech-border);
  border-radius: 8px;
  background: radial-gradient(ellipse at 50% 40%, #0d1b2e 0%, #060912 70%);
}
.bg-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
}
.edge-layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  pointer-events: none;
}
.edge-layer path.edge-line,
.edge-layer text.edge-label {
  pointer-events: stroke;
  cursor: pointer;
}
.edge-line {
  fill: none;
  stroke-width: 2.2;
}
.edge-label {
  fill: #b8c7dc;
  font-size: 11px;
  font-family: var(--tech-font-mono);
  text-anchor: middle;
  paint-order: stroke;
  stroke: #060912;
  stroke-width: 3;
}
.edge-draft {
  fill: none;
  stroke-width: 2;
  stroke-dasharray: 5 4;
}
.wf-node {
  position: absolute;
  z-index: 2;
  border-radius: 8px;
  padding: 0;
  cursor: grab;
  user-select: none;
  border: 1px solid var(--tech-border);
  background: linear-gradient(180deg, rgba(13, 21, 38, 0.92), rgba(10, 14, 26, 0.92));
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.wf-node:hover {
  border-color: var(--tech-accent);
}
.wf-node.selected {
  border-color: var(--tech-accent);
  box-shadow: 0 0 0 1px var(--tech-accent), var(--tech-glow);
}
.node-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-bottom: 1px solid var(--tech-border);
  cursor: grab;
}
.node-title {
  color: var(--tech-accent);
  font-family: var(--tech-font-mono);
  font-size: 13px;
}
.node-del {
  cursor: pointer;
  color: var(--tech-danger);
  font-size: 13px;
  padding: 0 4px;
}
.node-body {
  padding: 6px 10px;
}
.node-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: var(--tech-font-mono);
}
.port {
  position: absolute;
  top: 22px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #060912;
  font-weight: 700;
}
.port-out {
  right: -9px;
  background: var(--tech-accent);
  cursor: crosshair;
  box-shadow: var(--tech-glow);
}
.port-in {
  left: -9px;
  background: var(--tech-accent-2);
}
</style>
