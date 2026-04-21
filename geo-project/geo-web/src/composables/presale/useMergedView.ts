/**
 * useMergedView — 详情页 MergedViewDTO 的 provide / inject 封装。
 *
 * 背景:
 *   详情页(PresaleReportDetail.vue)加载 ReportDetailVO,用 mergeSnapshot 合成
 *   MergedViewDTO,然后把它连同 loading/error/版本元信息 provide 给子孙组件。
 *   Sidebar 需要读 meta(版本号、冻结、降级平台);Viewer 和 18 页 Page SFC 需要读
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
 *   - β·1:追加 reportCreatedAt(MergedViewDTO 无"报告创建时间"字段,Page01 封面
 *          等需要显示"ISSUED"日期。该字段源自 ReportDetailVO.createdAt,
 *          Detail 顶层取出并注入。向后兼容追加,不影响 α·2 既有消费方)
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
   * 报告主表创建时间(ReportDetailVO.createdAt,RFC3339 带 +08:00)。
   *
   * 为什么在这里:
   *   MergedViewDTO 本身没有"报告创建时间"字段,P1·B buildMeta 也没有把
   *   raw.meta.generated_at 提升到 merged view。Page01 封面的 ISSUED 区块
   *   需要一个日期,目前用 reportCreatedAt 代替。
   *
   * TODO:未来若 MergedViewMeta 补上 generated_at,优先用那个(精确到"生成完成时刻"),
   *       本字段降级为 fallback。
   *
   * β·1 新增(α·2 合入时未提供,Codex 合入本批后重新 provide;向后兼容追加)。
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
 * 在 Sidebar / Viewer / 18 页 Page SFC 里调用,拿到详情页上下文。
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
