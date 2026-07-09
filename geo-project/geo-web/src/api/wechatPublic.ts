import request from '@/api/request'
import type { R } from '@/types'

export interface WechatPublicArticle {
  id: number
  title: string
  digest?: string | null
  coverUrl?: string | null
  articleUrl: string
  platformArticleId: string
  publishedAt?: string | null
}

export interface WechatPublicArticleList {
  brandName?: string | null
  accountName?: string | null
  visualUrl?: string | null
  publicPhone?: string | null
  publicAddress?: string | null
  page: number
  size: number
  total: number
  articles: WechatPublicArticle[]
}

export function getWechatPublicArticles(publicSlug: string, params?: { page?: number; size?: number }) {
  return request.get<R<WechatPublicArticleList>>(`/public/wechat/mp/${publicSlug}/articles`, {
    params,
  })
}
