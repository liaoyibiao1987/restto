import { useAuthStore } from '../stores/auth';

/**
 * 按钮级权限指令：v-permission="'system:user:create'"
 * - 超管或拥有该权限码 → 保留元素；
 * - 否则在 mounted 前从 DOM 移除，避免闪烁与误点。
 *
 * @param {string} code 所需权限码
 * @returns {object} Vue 自定义指令定义
 */
function evaluate(code) {
  const auth = useAuthStore();
  if (auth.isAdmin) return true;
  return auth.permissions ? auth.permissions.has(code) : false;
}

const permissionDirective = {
  mounted(el, binding) {
    const code = binding.value;
    if (!code || evaluate(code)) {
      return;
    }
    if (el.parentNode) {
      el.parentNode.removeChild(el);
    } else {
      // 父节点尚未挂载时隐藏，避免渲染
      el.style.display = 'none';
    }
  },
};

export default permissionDirective;
