import { createApp, h, type DefineComponent } from 'vue';
import { createInertiaApp } from '@inertiajs/vue3';
import '../css/app.css';
import { initializeTheme } from './lib/theme';
import { initializeUiSounds } from './lib/uiSounds';
initializeTheme();
initializeUiSounds();
const pages = import.meta.glob<{ default: DefineComponent }>('./pages/*.vue');
createInertiaApp({
  resolve: async (name) => {
    const load = pages[`./pages/${name}.vue`];
    if (!load) throw new Error('Página não disponível');
    return (await load()).default;
  },
  setup({ el, App, props, plugin }) { createApp({ render: () => h(App, props) }).use(plugin).mount(el); },
});
