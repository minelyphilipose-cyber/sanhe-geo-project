/**
 * useMergedView — 详情页 MergedViewDTO 的 provide / inject 封装。
 *
 * 背景:
 *   详情页(PresaleReportDetail.vue)加载 ReportDetailVO,用 mergeSnapshot 合成
 *   MergedViewDTO,然后把它连同 loading/error/版本元信息 provide 给子孙组件。
 *   Sidebar 需要读 meta(版本号、冻结、降级平台);Viewer 和 19 页 Page SFC 需要读
 *   MergedViewDTO 的全部业务字段。如果每级组件都写 provide/inject + Symbol,
 *   代码噪音大且容易类型漂移,所以抽成 composable。
 *
 * 设计:
 *   - `provideMergedViewContext(ctx)` 在 Detail 顶层调用一次
 *   - `useMergedView()` 在子孙组件调用,返回的对象字段与 ctx 一致
 *   - 未正确 provide 时 inject 返回 undefined;本 composable 在 dev 抛异常便于定位
 *
 * 数据层语义:
 *   - loading === true 时 mergedView 允许为 null(首次渲染未 ready)
 *   - error 非 null 时 mergedView 可能是旧数据或 null,视 Detail 实现决定
 *   - generation_status !== 'DONE' 时 Detail 顶层会构造**降级视图**:
 *     meta + 客户信息(brand_name / industry / industry_role / region / user_demand)可用,
 *     但业务字段(test_summary / scores / merged_* 等)为空。消费方必须用
 *     meta.generation_status === 'DONE' 做守卫,不得在非 DONE 时访问业务字段。
 *
 * 版本演进:
 *   - α·2:初版,含 mergedView/currentVersionNo/loading/error/refresh/switchVersion
 *   - β·1/W4:追加 reportCreatedAt(Page01 封面等需要显示"ISSUED"日期)。
 *            Detail 顶层按 `meta.generated_at ?? reportDetail.createdAt` 计算并注入。
 *
 * 不在本 composable 内做的事:
 *   - fetch / mergeSnapshot:Detail 顶层做
 *   - 写动作(freeze / delete ...):Sidebar 直接 import API 函数做
 *   - 路由跳转:各组件用 vue-router 自行处理
 */

import { inject, provide, type InjectionKey, type Ref } from 'vue'
import type { MergedViewDTO } from '@/types/presale'

/**
 * 详情页共享上下文。
 *
 * 所有字段都是 ref,子组件通过 `.value` 读取。不使用 reactive 对象,
 * 因为 ref 在 inject 后保持响应性更稳。
 */
export interface MergedViewContext {
  /** 合并视图(P1 mock 期前端合成);非 DONE 或加载中可能为 null。 */
  mergedView: Ref<MergedViewDTO | null>

  /** 当前展示的 versionNo(和 mergedView.meta.version_no 一致,提出来便于 Sidebar 绑定)。 */
  currentVersionNo: Ref<number | null>

  /** 加载态。首次进入详情页或切换版本时 true。 */
  loading: Ref<boolean>

  /** 错误信息(fetch / JSON.parse / mergeSnapshot 任一环节失败的消息);成功时 null。 */
  error: Ref<string | null>

  /** 触发当前详情重新加载。Sidebar 在 derive / freeze / retry 等写动作成功后调用。 */
  refresh: () => Promise<void>

  /** 切换到指定 versionNo。Sidebar 版本下拉选中项变化时调用。 */
  switchVersion: (versionNo: number) => Promise<void>

  /**
   * 报告展示时间(ISSUED)。
   * 计算优先级: `mergedView.meta.generated_at`(生成完成时刻)
   *        > `ReportDetailVO.createdAt`(报告创建时刻)。
   */
  reportCreatedAt: Ref<string | null>
}

/**
 * typed InjectionKey。放在模块顶层,保证 provide/inject 两端用同一个引用。
 * 不导出,外部只通过 provideMergedViewContext / useMergedView 使用。
 */
const MergedViewKey: InjectionKey<MergedViewContext> = Symbol('MergedViewContext')

/**
 * 在详情页顶层(PresaleReportDetail.vue)调用,把上下文注入子孙组件。
 */
export function provideMergedViewContext(ctx: MergedViewContext): void {
  provide(MergedViewKey, ctx)
}

/**
 * 在 Sidebar / Viewer / 19 页 Page SFC 里调用,拿到详情页上下文。
 *
 * 若没有祖先调用 provideMergedViewContext(场景:组件被单独使用或单测脱离 Detail),
 * 抛异常,避免静默的 undefined 深入模板。
 */
export function useMergedView(): MergedViewContext {
  const ctx = inject(MergedViewKey)
  if (!ctx) {
    throw new Error(
      '[useMergedView] MergedViewContext not provided. ' +
        'Did you forget to call provideMergedViewContext() in PresaleReportDetail.vue?'
    )
  }
  return ctx
}
