<!--
 * Copyright (C) 2026 qwerty-a11y
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -->
<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const account = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    await userStore.login(account.value, password.value)
    router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="login" @submit.prevent="handleSubmit">
    <h1>登录</h1>
    <label>
      账号
      <input v-model="account" type="text" maxlength="20" required autocomplete="username" />
    </label>
    <label>
      密码
      <input v-model="password" type="password" minlength="6" maxlength="32" required autocomplete="current-password" />
    </label>
    <p v-if="error" class="error">{{ error }}</p>
    <button type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
  </form>
</template>

<style scoped>
.login {
  max-width: 20rem;
  margin: 4rem auto;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.login label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.login input {
  padding: 0.5rem;
}

.login button {
  padding: 0.5rem;
  cursor: pointer;
}

.error {
  color: #c0392b;
  font-size: 0.9rem;
}
</style>
