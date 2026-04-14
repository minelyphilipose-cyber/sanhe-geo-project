import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDictItems, type DictGroup, type DictItem } from '@/api/dict'

export const useDictStore = defineStore('dict', () => {
  const dictGroup = ref<DictGroup>({})
  const loaded = ref(false)
  let loadingPromise: Promise<void> | null = null

  async function fetchDicts(force = false) {
    if (loaded.value && !force) return
    if (!loadingPromise) {
      loadingPromise = getDictItems()
        .then(({ data }) => {
          dictGroup.value = data.data || {}
          loaded.value = true
        })
        .catch(() => {
          dictGroup.value = {}
          loaded.value = false
        })
        .finally(() => {
          loadingPromise = null
        })
    }
    await loadingPromise
  }

  async function ensureLoaded() {
    await fetchDicts(false)
  }

  async function reload() {
    await fetchDicts(true)
  }

  function options(dictType: string): DictItem[] {
    return dictGroup.value[dictType] || []
  }

  function label(dictType: string, dictKey?: string | null) {
    if (!dictKey) return '-'
    const hit = options(dictType).find((item) => item.dictKey === dictKey)
    return hit?.dictValue || dictKey
  }

  return {
    dictGroup,
    loaded,
    ensureLoaded,
    reload,
    options,
    label,
  }
})
