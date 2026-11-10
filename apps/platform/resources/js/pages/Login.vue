<script setup lang="ts">
import { ref } from 'vue';
import { useForm } from '@inertiajs/vue3';
import BrandMark from '../components/BrandMark.vue';
import SessionActions from '../components/SessionActions.vue';
import NotebookArt from '../components/NotebookArt.vue';
const form = useForm({ email: '', password: '' });
const showPassword = ref(false), passwordInput = ref<HTMLInputElement | null>(null);
function login() {
  form.post('/login', { onSuccess: () => form.reset('password'), onError: () => passwordInput.value?.focus() });
}
</script>
<template>
  <main class="login-page">
    <header class="login-top"><BrandMark /><SessionActions :allow-logout="false" /></header>
    <div class="login-content">
    <section class="login-welcome" aria-labelledby="welcome-title"><p class="chapter-label"><span class="chapter-rule" />SEU INGLÊS, NO SEU TEMPO</p><h1 id="welcome-title">Seu inglês,<br>página por <em>página.</em></h1><p>Leia. Escreva. Descubra o que você consegue dizer.</p><NotebookArt /><span class="welcome-note">Pequenas missões para o seu dia a dia.</span></section>
    <section class="login-layout" aria-labelledby="login-title">
    <div class="page-heading"><span class="login-bookmark" aria-hidden="true">↗</span><h2 id="login-title">Seu caderno espera.</h2><p class="muted">Entre para continuar sua trilha de inglês.</p></div>
    <form @submit.prevent="login" :aria-busy="form.processing">
      <label for="login-email">E-mail</label>
      <input id="login-email" v-model="form.email" type="email" inputmode="email" autocomplete="username" autocapitalize="none" :spellcheck="false" :aria-invalid="!!form.errors.email" :aria-describedby="form.errors.email ? 'login-error' : undefined" required @input="form.clearErrors('email')">
      <label for="login-password">Senha</label>
      <div class="password-field"><input id="login-password" ref="passwordInput" v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" :aria-invalid="!!form.errors.email" :aria-describedby="form.errors.email ? 'login-error' : undefined" required @input="form.clearErrors('email')"><button type="button" class="password-toggle icon-button" :aria-label="showPassword ? 'Ocultar senha' : 'Mostrar senha'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/><path v-if="showPassword" d="m3 3 18 18"/></svg></button></div>
      <p v-if="form.errors.email" id="login-error" class="notice" role="alert">{{ form.errors.email }}</p>
      <button class="primary-action" :disabled="form.processing"><span v-if="form.processing" class="spinner" aria-hidden="true" />{{ form.processing ? 'Entrando…' : 'Entrar' }}<span v-if="!form.processing" aria-hidden="true">→</span></button>
    </form>
    <p class="page-note"><span class="status-dot" aria-hidden="true" />Prévia editorial · conteúdo em revisão</p>
    </section>
    </div>
    <footer class="login-footer">Leitura e escrita. No seu tempo.</footer>
  </main>
</template>
