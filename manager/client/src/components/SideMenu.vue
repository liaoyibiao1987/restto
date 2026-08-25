<template>
  <template v-for="node in visibleNodes" :key="node.id">
    <!-- 目录：带子菜单 -->
    <el-sub-menu v-if="node.children && node.children.length" :index="node.path || String(node.id)">
      <template #title>
        <span class="menu-title">{{ node.menuName }}</span>
      </template>
      <side-menu :nodes="node.children" />
    </el-sub-menu>
    <!-- 菜单叶子 -->
    <el-menu-item v-else :index="node.path">
      <span class="menu-title">{{ node.menuName }}</span>
    </el-menu-item>
  </template>
</template>

<script>
/**
 * 递归侧边栏菜单：目录用 el-sub-menu，菜单叶子用 el-menu-item（router 模式按 path 导航）。
 * visible=0 的菜单（如隐藏的设计器路由）不在侧边栏显示。
 */
export default {
  name: 'SideMenu',
  props: {
    nodes: {
      type: Array,
      default: () => [],
    },
  },
  computed: {
    /**
     * 过滤掉 visible=0（隐藏路由）的菜单。
     *
     * @returns {Array} 可见菜单
     */
    visibleNodes() {
      return (this.nodes || []).filter((n) => n.visible !== 0);
    },
  },
};
</script>

<style scoped>
.menu-title {
  font-size: 14px;
  letter-spacing: 0.5px;
}
</style>
