import { defineStore } from 'pinia';
import { fetchInfo, fetchMenus } from '../api/auth';

/**
 * 鉴权状态：token / 用户信息 / 角色 / 权限码 / 菜单树，持久化 token+username 到 localStorage。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    username: localStorage.getItem('username') || '',
    nickname: localStorage.getItem('nickname') || '',
    roles: JSON.parse(localStorage.getItem('roles') || '[]'),
    permissions: new Set(JSON.parse(localStorage.getItem('permissions') || '[]')),
    menus: JSON.parse(localStorage.getItem('menus') || '[]'),
    loaded: false,
  }),
  getters: {
    /** 是否超管（含 admin 角色编码）。 */
    isAdmin: (state) => Array.isArray(state.roles) && state.roles.includes('admin'),
    /** 是否已拉取过用户信息/菜单。 */
    isProfileLoaded: (state) => state.loaded,
  },
  actions: {
    /**
     * 保存登录态。
     *
     * @param {string} token JWT
     * @param {string} username 用户名
     */
    setAuth(token, username) {
      this.token = token;
      this.username = username;
      localStorage.setItem('token', token);
      localStorage.setItem('username', username);
    },
    /**
     * 拉取当前用户信息 + 菜单树（登录后或刷新页面后调用一次）。
     *
     * @returns {Promise<void>}
     */
    async fetchProfile() {
      const [info, menus] = await Promise.all([fetchInfo(), fetchMenus()]);
      this.nickname = info.nickname || info.username || this.username;
      this.roles = info.roles || [];
      this.permissions = new Set(info.permissionCodes || []);
      this.menus = menus || [];
      this.loaded = true;
      localStorage.setItem('nickname', this.nickname);
      localStorage.setItem('roles', JSON.stringify(this.roles));
      localStorage.setItem('permissions', JSON.stringify([...this.permissions]));
      localStorage.setItem('menus', JSON.stringify(this.menus));
    },
    /**
     * 判断是否拥有某权限码（超管恒为 true）。
     *
     * @param {string} code 权限码
     * @returns {boolean}
     */
    hasPermission(code) {
      if (this.isAdmin) return true;
      return this.permissions ? this.permissions.has(code) : false;
    },
    /**
     * 清除登录态。
     */
    logout() {
      this.token = '';
      this.username = '';
      this.nickname = '';
      this.roles = [];
      this.permissions = new Set();
      this.menus = [];
      this.loaded = false;
      ['token', 'username', 'nickname', 'roles', 'permissions', 'menus'].forEach((k) =>
        localStorage.removeItem(k),
      );
    },
  },
});
