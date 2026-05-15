import { profileForUrl } from './contentProfiles'
import type { ContentScriptFillProfile } from './contentProfiles'

let activeTaskId: number | null = null

export function activatePublishListener(taskId: number) {
  activeTaskId = taskId
}

export function handlePublishClick(target: EventTarget | null, pageUrl: string = window.location.href): boolean {
  if (!activeTaskId || !(target instanceof Element)) return false
  const profile = profileForUrl(pageUrl)
  if (!profile || !isCompletionButtonClick(target, profile)) return false
  void chrome.runtime.sendMessage({
    type: 'GEO_TASK_PUBLISHED',
    payload: {
      taskId: activeTaskId,
      href: pageUrl,
      platform: profile.platform,
    },
  })
  activeTaskId = null
  return true
}

function isCompletionButtonClick(target: Element, profile: ContentScriptFillProfile): boolean {
  const selectorMatched = [
    ...profile.publishButtonSelectors,
    ...profile.draftButtonSelectors,
  ].some(selector => target.closest(selector))
  if (selectorMatched) return true

  const clickable = target.closest('button,a,[role="button"],[class*="button"],[class*="btn"]')
  if (!clickable) return false
  const label = buttonLabel(clickable)
  if (['取消发布', '不发布'].some(keyword => label.includes(keyword))) return false
  return profile.completionButtonTextKeywords.some(keyword => label.includes(keyword))
}

function buttonLabel(element: Element): string {
  const value = element instanceof HTMLInputElement ? element.value : ''
  return `${element.textContent || ''} ${element.getAttribute('aria-label') || ''} ${value}`.replace(/\s+/g, '')
}
