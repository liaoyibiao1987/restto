<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="title">rustto 备份系统</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="onSubmit">
          登录
        </el-button>
      </el-form>
      <p class="hint">默认账号 admin / admin123</p>
    </el-card>
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
 * 提交登录：校验 -> 请求 -> 保存 token -> 跳首页。
 */
async function onSubmit() {
  try {
    await formRef.value.validate();
    loading.value = true;
    const data = await login(form.username, form.password);
    auth.setAuth(data.token, data.username);
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
  background: #f0f2f5;
}
.login-card {
  width: 360px;
}
.title {
  text-align: center;
  margin-bottom: 20px;
}
.hint {
  text-align: center;
  color: #999;
  font-size: 12px;
  margin-top: 12px;
}
</style>
