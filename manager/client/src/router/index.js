import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'nodes', component: () => import('../views/NodeManage.vue') },
      { path: 'tasks', component: () => import('../views/TaskManage.vue') },
      { path: 'records', component: () => import('../views/RecordHistory.vue') },
      { path: 'binaries', component: () => import('../views/BinaryManage.vue') },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

/**
 * 全局前置守卫：未登录则跳 /login。
 */
router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.path !== '/login' && !auth.token) {
    return { path: '/login' };
  }
  return true;
});

export default router;
