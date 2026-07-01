import deepseekLogo from '@/assets/ai-model-logos/deepseek-color.png'
import doubaoLogo from '@/assets/ai-model-logos/doubao.png'
import qwenLogo from '@/assets/ai-model-logos/qwen-color.png'
import yuanbaoLogo from '@/assets/ai-model-logos/yuanbao-color.svg'
import wenxinLogo from '@/assets/ai-model-logos/文心一言.png'
import { normalizeObjectStorageUrl } from '@/utils/objectStorageUrl'

export interface AiPlatformLogoSource {
  platformId?: number | null
  platformCode?: string | null
  code?: string | null
  platformName?: string | null
  name?: string | null
  platformLogoUrl?: string | null
  platformLogoObjectKey?: string | null
  logo?: string | null
}

const builtinLogos: Record<string, string> = {
  doubao: doubaoLogo,
  deepseek: deepseekLogo,
  tongyi: qwenLogo,
  qwen: qwenLogo,
  wenxin: wenxinLogo,
  ernie: wenxinLogo,
  yuanbao: yuanbaoLogo,
  hunyuan: yuanbaoLogo,
}

function normalizeLogoKey(value?: string | null) {
  return String(value || '').trim().toLowerCase()
}

export function builtinAiPlatformLogo(code?: string | null) {
  const key = normalizeLogoKey(code)
  return builtinLogos[key] || ''
}

export function uploadedAiPlatformLogoSrc(platform: AiPlatformLogoSource) {
  const logoVersion = platform.platformLogoObjectKey
    || normalizeObjectStorageUrl(platform.platformLogoUrl || platform.logo)
  if (!logoVersion || !platform.platformId) return ''
  return `/api/public/platform-configs/${platform.platformId}/logo?v=${encodeURIComponent(logoVersion)}`
}

export function aiPlatformLogoSrc(platform: AiPlatformLogoSource) {
  return uploadedAiPlatformLogoSrc(platform)
    || builtinAiPlatformLogo(platform.platformCode || platform.code)
}

export function fallbackAiPlatformLogo(event: Event, platform: AiPlatformLogoSource) {
  const image = event.target as HTMLImageElement
  const fallback = builtinAiPlatformLogo(platform.platformCode || platform.code)
  if (fallback && image.dataset.logoFallbackApplied !== 'true') {
    image.dataset.logoFallbackApplied = 'true'
    image.src = fallback
  } else {
    image.style.display = 'none'
  }
}
