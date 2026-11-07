import { ref } from 'vue';
export const darkTheme = ref(false);
export function applyTheme(dark: boolean) {
  darkTheme.value = dark;
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
}
export function initializeTheme() {
  let saved: string | null = null;
  try { saved = localStorage.getItem('idiomas-theme'); } catch { /* Storage may be unavailable. */ }
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  applyTheme(saved ? saved === 'dark' : system.matches);
  system.addEventListener('change', event => {
    try { if (localStorage.getItem('idiomas-theme')) return; } catch { return; }
    applyTheme(event.matches);
  });
}
export function toggleTheme() {
  applyTheme(!darkTheme.value);
  try { localStorage.setItem('idiomas-theme', darkTheme.value ? 'dark' : 'light'); } catch { /* Keep the current session usable. */ }
}
