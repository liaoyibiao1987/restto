import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/dark/css-vars.css';
import './styles/index.css';
import App from './App.vue';
import router from './router';
import permissionDirective from './directives/permission';

/**
 * 应用入口：开启暗色主题，装配 Pinia、Vue Router、Element Plus 与权限指令后挂载。
 */
document.documentElement.classList.add('dark');

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.use(ElementPlus);
app.directive('permission', permissionDirective);
app.mount('#app');
