import { computed, reactive, ref, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElMessage } from 'element-plus'
import type { ArticleDetailResponse, ArticleDraft } from '@/types'
import {
  getContentArticleDetail,
  saveContentArticleRevision,
} from '@/api/content'
import {
  getBrandImageFolders,
  getBrandMaterialPreviewUrl,
} from '@/api/customer'

type UseArticleDetailRevisionOptions = {
  load: () => Promise<void>
  submitting: Ref<boolean>
}

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

export function useArticleDetailRevision(options: UseArticleDetailRevisionOptions) {
  const router = useRouter()

  const detailVisible = ref(false)
  const detailData = ref<ArticleDetailResponse | null>(null)
  const detailViewMode = ref<'preview' | 'markdown'>('preview')
  const currentArticleId = ref<number | null>(null)
  const articleImagePreviewUrls = ref<Record<string, string>>({})
  const detailCoverImageUrl = computed(() => normalizeDisplayImageUrl(detailData.value?.article.coverImageUrl))

  const revisionVisible = ref(false)
  const revisionViewMode = ref<'preview' | 'markdown'>('markdown')
  const revisionForm = reactive({
    title: '',
    contentMarkdown: '',
    note: '',
  })

  const detailMarkdown = computed(() => detailData.value?.versions?.[0]?.contentMarkdown || '')
  const detailHtml = computed(() => renderArticlePreviewMarkdown(detailMarkdown.value || ''))
  const revisionHtml = computed(() => renderArticlePreviewMarkdown(revisionForm.contentMarkdown || ''))

  async function openDetail(articleId: number) {
    try {
      articleImagePreviewUrls.value = {}
      const { data } = await getContentArticleDetail(articleId)
      detailData.value = data.data
      detailViewMode.value = 'preview'
      await loadArticleImagePreviewUrls(detailMarkdown.value, data.data.project?.brandId || null, data.data.project?.id)
      detailVisible.value = true
    } catch {
      ElMessage.error('加载详情失败')
    }
  }

  async function openRevision(row: ArticleDraft) {
    currentArticleId.value = row.id
    revisionForm.title = row.title
    revisionForm.note = ''
    revisionViewMode.value = 'markdown'
    articleImagePreviewUrls.value = {}
    try {
      const { data } = await getContentArticleDetail(row.id)
      revisionForm.contentMarkdown = data.data.versions?.[0]?.contentMarkdown || ''
      await loadArticleImagePreviewUrls(revisionForm.contentMarkdown, data.data.project?.brandId || null, data.data.project?.id)
    } catch {
      revisionForm.contentMarkdown = ''
    }
    revisionVisible.value = true
  }

  function openRevisionFromDetail() {
    if (!detailData.value) return
    currentArticleId.value = detailData.value.article.id
    revisionForm.title = detailData.value.article.title
    revisionForm.contentMarkdown = detailData.value.versions?.[0]?.contentMarkdown || ''
    revisionForm.note = ''
    revisionViewMode.value = 'markdown'
    void loadArticleImagePreviewUrls(revisionForm.contentMarkdown, detailData.value.project?.brandId || null, detailData.value.project?.id)
    revisionVisible.value = true
  }

  async function submitRevision() {
    if (!currentArticleId.value) return
    if (!revisionForm.contentMarkdown.trim()) {
      ElMessage.warning('正文不能为空')
      return
    }
    options.submitting.value = true
    try {
      await saveContentArticleRevision(currentArticleId.value, {
        title: revisionForm.title || undefined,
        contentMarkdown: revisionForm.contentMarkdown,
        note: revisionForm.note || undefined,
      })
      revisionVisible.value = false
      ElMessage.success('修订保存成功')
      await options.load()
      if (detailVisible.value && detailData.value?.article.id === currentArticleId.value) {
        const { data } = await getContentArticleDetail(currentArticleId.value)
        detailData.value = data.data
        detailViewMode.value = 'preview'
        await loadArticleImagePreviewUrls(detailMarkdown.value, data.data.project?.brandId || null, data.data.project?.id)
      }
    } finally {
      options.submitting.value = false
    }
  }

  function handleDetailStyleRenderCommand(command: string | number | object) {
    if (!detailData.value) return
    if (String(command) === 'wechat') {
      router.push({
        path: `/admin/content/articles/${detailData.value.article.id}/wechat-render`,
      })
    }
  }

  function renderArticlePreviewMarkdown(content: string) {
    const html = markdown.render(normalizeArticlePreviewMarkdown(content))
    const previewUrls = articleImagePreviewUrls.value
    if (!Object.keys(previewUrls).length) {
      return html
    }
    const template = document.createElement('template')
    template.innerHTML = html
    template.content.querySelectorAll('img').forEach((image) => {
      const originalSrc = image.getAttribute('src') || ''
      const previewUrl = previewUrls[originalSrc]
      if (previewUrl) {
        image.setAttribute('data-source-src', originalSrc)
        image.setAttribute('src', previewUrl)
      }
    })
    return template.innerHTML
  }

  function normalizeDisplayImageUrl(value?: string | null) {
    const url = value?.trim()
    if (!url) return ''
    if (/^https?:\/\//i.test(url)) return withArticlePreviewCacheBuster(url)
    if (url.startsWith('data:')) return url
    if (url.startsWith('//')) return `${window.location.protocol}${url}`
    if (url.startsWith('/')) return withArticlePreviewCacheBuster(`${window.location.origin}${url}`)
    return url
  }

  function withArticlePreviewCacheBuster(url: string) {
    if (!url.includes('/api/public/brand-materials/')) return url
    try {
      const parsed = new URL(url)
      parsed.searchParams.set('preview', 'article')
      return parsed.toString()
    } catch {
      return url
    }
  }

  async function loadArticleImagePreviewUrls(markdownContent: string, brandId?: number | null, projectId?: number | null) {
    if (!brandId || !markdownContent.trim()) {
      articleImagePreviewUrls.value = {}
      return
    }
    const imageUrls = extractMarkdownImageUrls(markdownContent)
    if (!imageUrls.length) {
      articleImagePreviewUrls.value = {}
      return
    }
    try {
      const { data } = await getBrandImageFolders(brandId, {
        projectId: projectId || undefined,
        activeOnly: true,
        includeMaterials: true,
      })
      const materials = (data.data || []).flatMap((folder) => folder.materials || [])
      const materialByUrl = new Map(materials.map((material) => [material.fileUrl, material]))
      const next: Record<string, string> = {}
      await Promise.all(imageUrls.map(async (url) => {
        const material = materialByUrl.get(url)
        if (!material) return
        const previewRes = await getBrandMaterialPreviewUrl(brandId, material.id)
        next[url] = previewRes.data.data.url
      }))
      articleImagePreviewUrls.value = next
    } catch {
      articleImagePreviewUrls.value = {}
    }
  }

  return {
    detailVisible,
    detailData,
    detailViewMode,
    detailCoverImageUrl,
    detailMarkdown,
    detailHtml,
    revisionVisible,
    revisionViewMode,
    revisionForm,
    revisionHtml,
    openDetail,
    openRevision,
    openRevisionFromDetail,
    submitRevision,
    handleDetailStyleRenderCommand,
  }
}

function extractMarkdownImageUrls(content: string) {
  const urls = new Set<string>()
  const pattern = /!\[[^\]]*]\(([^)\s]+)(?:\s+"[^"]*")?\)/g
  let match: RegExpExecArray | null
  while ((match = pattern.exec(content)) !== null) {
    if (match[1]) {
      urls.add(match[1])
    }
  }
  const htmlImagePattern = /<img\b[^>]*\bsrc=(["'])(.*?)\1[^>]*>/gi
  while ((match = htmlImagePattern.exec(content)) !== null) {
    if (match[2]) {
      urls.add(decodeHtmlAttribute(match[2]))
    }
  }
  return Array.from(urls)
}

function normalizeArticlePreviewMarkdown(content: string) {
  return content.replace(/(?:<p\b[^>]*>\s*)?<img\b([^>]*)>(?:\s*<\/p>)?/gi, (_matched, attributes: string) => {
    const src = readHtmlAttribute(attributes, 'src')
    if (!src) return ''
    const alt = readHtmlAttribute(attributes, 'alt') || '图片'
    return `\n![${escapeMarkdownImageAlt(alt)}](${src})\n`
  })
}

function readHtmlAttribute(attributes: string, name: string) {
  const pattern = new RegExp(`\\b${name}=("|')([^"']*)\\1`, 'i')
  const match = attributes.match(pattern)
  return match?.[2] ? decodeHtmlAttribute(match[2]) : ''
}

function decodeHtmlAttribute(value: string) {
  if (typeof document === 'undefined') {
    return value.replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;/g, "'")
  }
  const textarea = document.createElement('textarea')
  textarea.innerHTML = value
  return textarea.value
}

function escapeMarkdownImageAlt(value: string) {
  return value.replace(/\\/g, '\\\\').replace(/]/g, '\\]')
}
