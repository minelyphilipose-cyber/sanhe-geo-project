import request from './request'
import type { R, PresaleQuestionSet, PresaleQuestionSetDetail, DispatchTaskItem } from '@/types'

export function getPresaleQuestionSets(projectId: number) {
  return request.get<R<PresaleQuestionSet[]>>('/presale/question-sets', { params: { projectId } })
}

export function getPresaleQuestionSetDetail(setId: number) {
  return request.get<R<PresaleQuestionSetDetail>>(`/presale/question-sets/${setId}`)
}

export function generatePresaleQuestionSet(projectId: number, regenerate = false) {
  return request.post<R<PresaleQuestionSetDetail>>('/presale/question-sets/generate', { projectId, regenerate })
}

export function savePresaleQuestionSetItems(
  setId: number,
  items: Array<{
    id?: number
    content: string
    questionType: string
    source?: string
    sortOrder?: number
    isActive?: boolean
  }>,
) {
  return request.put<R<PresaleQuestionSetDetail>>(`/presale/question-sets/${setId}`, { items })
}

export function lockPresaleQuestionSet(setId: number) {
  return request.put<R<PresaleQuestionSet>>(`/presale/question-sets/${setId}/lock`)
}

export function startPresaleDiagnosis(projectId: number, questionSetId: number, remark?: string) {
  return request.post<R<DispatchTaskItem>>('/presale/diagnosis/start', { projectId, questionSetId, remark })
}

