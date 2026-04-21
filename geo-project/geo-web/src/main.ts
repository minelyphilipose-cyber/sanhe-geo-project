import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import '@fontsource/playfair-display/400.css'
import '@fontsource/playfair-display/900.css'
import '@fontsource/noto-serif-sc/400.css'
import '@fontsource/noto-serif-sc/500.css'
import '@fontsource/noto-serif-sc/700.css'
import '@fontsource/noto-sans-sc/400.css'
import '@fontsource/noto-sans-sc/500.css'
import '@fontsource/noto-sans-sc/700.css'
import '@fontsource/jetbrains-mono/400.css'
import '@/assets/presale/report-theme.css'

import App from './App.vue'
import router from './router'
import { vPermission } from './directives/permission'
import './styles/index.css'

const app = createApp(App)

// Pinia
app.use(createPinia())

// Router
app.use(router)

// Element Plus
app.use(ElementPlus, { locale: zhCn })

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 自定义指令
app.directive('permission', vPermission)

app.mount('#app')
