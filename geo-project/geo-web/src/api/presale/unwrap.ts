import type { AxiosResponse } from 'axios'
import type { R } from '@/types'

/**
 * 将 AxiosResponse<R<T>> 解包为 T。
 *
 * 背景:
 *   仓库共享的 @/api/request 拦截器**不自动解包 R<T>**(上游约定,影响所有模块)。
 *   对于返回 JSON 的业务接口,调用方真正需要的是 response.data.data 里的 T。
 *
 * 约定(仅 presale 模块):
 *   - 所有非 blob / arraybuffer 的接口一律走 unwrap(),不再混写 .then(res => res.data.data)
 *   - 二进制接口(如 PDF 导出)保持 AxiosResponse 原样,不过 unwrap
 *     (二进制在拦截器里被单独 bypass,见 @/api/request)
 *
 * 错误处理:
 *   拦截器已处理 code !== 0 的场景(rejected),走到这里的都是 code === 0 的成功响应。
 *   unwrap 本身不做业务校验,只是把嵌套拆平,类型安全。
 *
 * 用法:
 *   export function listReports(params: Q) {
 *     return unwrap(request.get<R<Page<Item>>>('/presale/reports', { params }))
 *   }
 *
 * 见 b·2·α·1 交付物 README "前端 API 解包规范"。
 */
export function unwrap<T>(p: Promise<AxiosResponse<R<T>>>): Promise<T> {
  return p.then((res) => res.data.data as T)
}
