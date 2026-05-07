import { profileForUrl } from '@/shared/fillProfiles'

let activeTaskId: number | null = null

export function activatePublishListener(taskId: number) {
  activeTaskId = taskId
}

export function handlePublishClick(target: EventTarget | null, pageUrl: string = window.location.href): boolean {
  if (!activeTaskId || !(target instanceof Element)) return false
  const profile = profileForUrl(pageUrl)
  if (!profile || !profile.publishButtonSelectors.some(selector => target.closest(selector))) return false
  void chrome.runtime.sendMessage({
    type: 'GEO_TASK_PUBLISHED',
    payload: {
      taskId: activeTaskId,
      href: pageUrl,
      platform: profile.platform,
    },
  })
  return true
}
