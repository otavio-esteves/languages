<script setup lang="ts">
import { useForm } from '@inertiajs/vue3';
const form = useForm({ email: '', password: '' });
function login() { form.post('/login', { onFinish: () => form.reset('password') }); }
</script>
<template><main class="login"><p class="eyebrow">IDIOMAS · EDITORIAL</p><h1>Preparar experiências de aprendizagem.</h1><p>Acesso da equipe para revisar o conteúdo antes da publicação.</p><form @submit.prevent="login"><label>E-mail<input v-model="form.email" type="email" autocomplete="username" required></label><label>Senha<input v-model="form.password" type="password" autocomplete="current-password" required></label><p v-if="form.errors.email" role="alert">{{ form.errors.email }}</p><button :disabled="form.processing">Entrar</button></form></main></template>
