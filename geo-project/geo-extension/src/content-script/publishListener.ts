import { profileForUrl } from './contentProfiles'
import type { ContentScriptFillProfile } from './contentProfiles'
import type { PublishCompletionAction } from '@/types/extension'

let activeTaskId: number | null = null
let feedbackObserver: MutationObserver | null = null

export function activatePublishListener(taskId: number) {
  activeTaskId = taskId
  startPublishFeedbackObserver()
}

export function handlePublishClick(target: EventTarget | null, pageUrl: string = window.location.href): boolean {
  if (!activeTaskId || !(target instanceof Element)) return false
  const profile = profileForUrl(pageUrl)
  if (!profile) return false
  const completion = classifyCompletionButtonClick(target, profile)
  if (!completion) return false
  reportCompletion(completion.action, pageUrl, profile.platform, completion.detectedText)
  return true
}

export function handlePublishFeedback(root: ParentNode = document.body, pageUrl: string = window.location.href): boolean {
  if (!activeTaskId) return false
  const profile = profileForUrl(pageUrl)
  if (!profile) return false
  const text = normalizeText(root.textContent || '')
  const keyword = profile.successFeedbackKeywords.find(item => text.includes(item))
  if (!keyword) return false
  reportCompletion('success_feedback', pageUrl, profile.platform, keyword)
  return true
}

function classifyCompletionButtonClick(
  target: Element,
  profile: ContentScriptFillProfile,
): { action: PublishCompletionAction, detectedText: string } | null {
  const draftSelectorMatched = profile.draftButtonSelectors.some(selector => target.closest(selector))
  if (draftSelectorMatched) return { action: 'draft_saved_clicked', detectedText: buttonLabel(target) }

  const publishSelectorMatched = profile.publishButtonSelectors.some(selector => target.closest(selector))
  if (publishSelectorMatched) return { action: 'publish_clicked', detectedText: buttonLabel(target) }

  const clickable = target.closest('button,a,[role="button"],[class*="button"],[class*="btn"]')
  if (!clickable) return null
  const label = buttonLabel(clickable)
  if (['取消发布', '不发布', '暂不发布', '发布设置'].some(keyword => label.includes(keyword))) return null
  if (['保存草稿', '保存到草稿', '保存草稿箱', '存草稿'].some(keyword => label.includes(keyword))) {
    return { action: 'draft_saved_clicked', detectedText: label }
  }
  if (profile.completionButtonTextKeywords.some(keyword => label.includes(keyword))) {
    return { action: 'publish_clicked', detectedText: label }
  }
  return null
}

function reportCompletion(
  action: PublishCompletionAction,
  pageUrl: string,
  platform: string,
  detectedText: string,
) {
  if (!activeTaskId) return
  void chrome.runtime.sendMessage({
    type: 'GEO_TASK_PUBLISHED',
    payload: {
      taskId: activeTaskId,
      href: pageUrl,
      platform,
      action,
      detectedText,
    },
  })
  activeTaskId = null
  stopPublishFeedbackObserver()
}

function startPublishFeedbackObserver() {
  stopPublishFeedbackObserver()
  if (!document.body) return
  feedbackObserver = new MutationObserver(() => {
    handlePublishFeedback()
  })
  feedbackObserver.observe(document.body, { childList: true, subtree: true, characterData: true })
}

function stopPublishFeedbackObserver() {
  feedbackObserver?.disconnect()
  feedbackObserver = null
}

function buttonLabel(element: Element): string {
  const value = element instanceof HTMLInputElement ? element.value : ''
  return normalizeText(`${element.textContent || ''} ${element.getAttribute('aria-label') || ''} ${value}`)
}

function normalizeText(value: string): string {
  return value.replace(/\s+/g, '')
}
