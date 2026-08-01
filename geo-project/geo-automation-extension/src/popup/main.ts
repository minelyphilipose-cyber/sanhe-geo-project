import { createApp, ref } from 'vue'

const App = {
  setup() {
    const installId = ref('')
    const workerStatus = ref('checking')

    chrome.runtime.sendMessage({ type: 'GEO_AUTOMATION_GET_INSTALL_ID' }, response => {
      installId.value = response?.installId || ''
    })

    chrome.runtime.sendMessage({ type: 'GEO_AUTOMATION_CURRENT_TASK' }, response => {
      workerStatus.value = response?.ok ? 'ready' : (response?.error || 'NO_ACTIVE_TASK')
    })

    return { installId, workerStatus }
  },
  template: `
    <main class="popup">
      <h1>GEO Automation</h1>
      <dl>
        <dt>Install ID</dt>
        <dd>{{ installId || '-' }}</dd>
        <dt>Worker</dt>
        <dd>{{ workerStatus }}</dd>
      </dl>
    </main>
  `,
}

createApp(App).mount('#app')

const style = document.createElement('style')
style.textContent = `
  body { margin: 0; min-width: 320px; font: 13px/1.5 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #172033; }
  .popup { padding: 14px; display: grid; gap: 12px; }
  h1 { margin: 0; font-size: 16px; }
  dl { margin: 0; display: grid; grid-template-columns: 82px minmax(0, 1fr); gap: 8px; }
  dt { color: #64748b; }
  dd { margin: 0; overflow-wrap: anywhere; }
`
document.head.appendChild(style)
