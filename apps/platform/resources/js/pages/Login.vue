<script setup lang="ts">
import { useForm } from '@inertiajs/vue3';
import ThemeToggle from '../components/ThemeToggle.vue';
import BrandMark from '../components/BrandMark.vue';
const form = useForm({ email: '', password: '' });
function login() { form.post('/login', { onFinish: () => form.reset('password') }); }
</script>
<template>
  <main class="login-layout">
    <div class="login-top"><BrandMark /><ThemeToggle /></div>
    <div class="page-heading"><p class="eyebrow">LEITURA E ESCRITA</p><h1>Um passo de cada vez.</h1><p class="muted">Entre para revisar a trilha de inglês.</p></div>
    <form @submit.prevent="login">
      <label>E-mail<input v-model="form.email" type="email" autocomplete="username" required></label>
      <label>Senha<input v-model="form.password" type="password" autocomplete="current-password" required></label>
      <p v-if="form.errors.email" class="notice" role="alert">{{ form.errors.email }}</p>
      <button :disabled="form.processing">{{ form.processing ? 'Entrando…' : 'Entrar' }}</button>
    </form>
    <p class="page-note">Espaço editorial · materiais em revisão.</p>
  </main>
</template>
