import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

/**
 * 动态视图解析：按菜单 component 字段（如 'system/UserManage' 或 'Dashboard'）
 * 映射到 src/views 下的懒加载组件。菜单管理真正驱动可访问路由。
 */
const viewModules = import.meta.glob('../views/**/*.vue');

/**
 * 按组件路径解析懒加载函数。
 *
 * @param {string} component 菜单 component 字段
 * @returns {Function|null} 懒加载函数，未匹配返回 null
 */
function resolveView(component) {
  if (!component) return null;
  const key = `../views/${component}.vue`;
  return viewModules[key] || null;
}

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  { path: '/403', name: 'Forbidden', component: () => import('../views/system/Forbidden.vue') },
  { path: '/404', name: 'NotFound', component: () => import('../views/system/NotFound.vue') },
  {
    path: '/',
    name: 'Layout',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      // 常驻默认路由：仪表盘（即便菜单未加载也可访问首页）
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { public: true },
      },
    ],
  },
  // 兜底路由：用 component 渲染 NotFound，且不要给它 name。
  //   - 必须是 component 而非 redirect 到 '/404'：redirect 在 router.resolve() 阶段
  //     （早于 beforeEach 守卫）就把 to 改写成 '/404'，守卫来不及注册动态路由。
  //   - 不能有 name：守卫末尾 return { ...to, replace: true } 时，若 to.name 存在，
  //     vue-router 会按 name 解析（重新落到本兜底路由），而非按 to.path 解析到刚注册
  //     的动态路由，刷新仍然 404。
  {
    path: '/:pathMatch(.*)*',
    component: () => import('../views/system/NotFound.vue'),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

/** 是否已注册过动态路由，避免重复 addRoute。 */
let dynamicLoaded = false;

/**
 * 根据菜单树注册动态路由（仅 menu_type=2 的菜单，目录不注册）。
 *
 * @param {Array} menus 菜单树
 */
function registerDynamicRoutes(menus) {
  const flatten = (nodes, acc) => {
    (nodes || []).forEach((n) => {
      if (n.menuType === 2 && n.path) {
        acc.push(n);
      }
      if (n.children && n.children.length) {
        flatten(n.children, acc);
      }
    });
    return acc;
  };
  const menuLeaves = flatten(menus, []);
  menuLeaves.forEach((menu) => {
    const component = resolveView(menu.component);
    if (!component) {
      // 找不到视图文件则跳过，避免空白页
      return;
    }
    const fullPath = menu.path.startsWith('/') ? menu.path : `/${menu.path}`;
    const name = `dyn_${menu.id}`;
    if (router.hasRoute(name)) return;
    router.addRoute('Layout', {
      path: fullPath.replace(/^\//, ''),
      name,
      component,
      meta: { permission: menu.perms, title: menu.menuName, menuId: menu.id },
    });
  });
  dynamicLoaded = true;
}

/**
 * 全局前置守卫：
 * - 登录页放行；其余未登录跳登录。
 * - 已登录但未拉取 profile → 拉取并注册动态路由，重新导航到目标。
 * - 目标路由 meta.permission 无权限 → 跳 403。
 */
router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.path === '/login') {
    return auth.token ? { path: '/' } : true;
  }
  if (!auth.token) {
    return { path: '/login' };
  }
  if (!dynamicLoaded) {
    try {
      await auth.fetchProfile();
      registerDynamicRoutes(auth.menus);
    } catch (e) {
      // profile 拉取失败（如 token 失效被 401 处理）→ 退回登录
      if (!auth.token) return { path: '/login' };
    }
    // 重新导航，使新增路由生效
    return { ...to, replace: true };
  }
  // 权限校验：有 meta.permission 且用户无权限 → 403
  if (to.meta && to.meta.permission && !auth.hasPermission(to.meta.permission)) {
    return { path: '/403' };
  }
  return true;
});

export default router;
