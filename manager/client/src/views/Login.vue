<template>
  <div class="login-wrap">
    <div class="login-card tech-card tech-glow">
      <div class="brand">
        <div class="brand-mark">◆</div>
        <h2 class="title">RUSTTO</h2>
        <div class="subtitle">分布式服务器数据备份系统</div>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            size="large"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          size="large"
          class="submit tech-glow"
          @click="onSubmit"
        >
          进入控制台
        </el-button>
      </el-form>
      <p class="hint">默认账号 admin / admin123</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { login } from '../api/auth';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const auth = useAuthStore();
const formRef = ref(null);
const loading = ref(false);

const form = reactive({ username: 'admin', password: '' });
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

/**
 * 提交登录：校验 -> 登录 -> 保存 token -> 拉取 profile（角色/权限/菜单）-> 跳首页。
 */
async function onSubmit() {
  try {
    await formRef.value.validate();
    loading.value = true;
    const data = await login(form.username, form.password);
    auth.setAuth(data.token, data.username);
    await auth.fetchProfile();
    ElMessage.success('登录成功');
    router.push('/');
  } catch (e) {
    // 校验失败或请求错误，拦截器已提示。
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background:
    radial-gradient(circle at 20% 20%, rgba(0, 229, 255, 0.12), transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(124, 92, 255, 0.12), transparent 40%),
    var(--tech-bg);
}
.login-card {
  width: 380px;
  padding: 36px 32px 28px;
  border-radius: 14px;
}
.brand {
  text-align: center;
  margin-bottom: 24px;
}
.brand-mark {
  font-size: 28px;
  color: var(--tech-accent);
  text-shadow: var(--tech-glow);
}
.title {
  margin: 8px 0 4px;
  font-family: var(--tech-font-mono);
  letter-spacing: 6px;
  color: var(--el-text-color-primary);
}
.subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  letter-spacing: 1px;
}
.submit {
  width: 100%;
  margin-top: 4px;
  font-weight: 700;
  letter-spacing: 2px;
}
.hint {
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 16px;
}
</style>
