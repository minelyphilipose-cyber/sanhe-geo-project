import type {
  PackageType, ProjectStage, QuestionType, QuestionPriority,
  PlatformPriority, ReportType, ReportStatus, RoleType,
  PlatformHealth, PartnerLevel, TrainingStatus, AlertSeverity,
} from '@/types'

export const PACKAGE_MAP: Record<PackageType, {
  label: string
  price: number
  duration: string
  color: string
}> = {
  trial_6980: { label: 'GEO 试点版', price: 698000, duration: '3个月', color: '#60A5FA' },
  standard_12800: { label: 'GEO 标准版', price: 1280000, duration: '12个月', color: '#2563EB' },
  growth_26800: { label: 'GEO 增长版', price: 2680000, duration: '12个月', color: '#1D4ED8' },
}

export const PARTNER_LEVEL_MAP: Record<PartnerLevel, {
  label: string
  recharge: number
  discountRate: number
}> = {
  level_29800: { label: '29800 档', recharge: 2980000, discountRate: 0.30 },
  level_59800: { label: '59800 档', recharge: 5980000, discountRate: 0.25 },
  level_99800: { label: '99800 档', recharge: 9980000, discountRate: 0.20 },
}

export const DEDUCTION_TABLE: Record<PackageType, Record<PartnerLevel, number>> = {
  trial_6980: { level_29800: 209400, level_59800: 174500, level_99800: 139600 },
  standard_12800: { level_29800: 384000, level_59800: 320000, level_99800: 256000 },
  growth_26800: { level_29800: 804000, level_59800: 670000, level_99800: 536000 },
}

export const PROJECT_STAGE_MAP: Record<ProjectStage, {
  label: string
  color: string
  order: number
}> = {
  pending_start: { label: '待启动', color: '#94A3B8', order: 1 },
  collecting_materials: { label: '资料收集中', color: '#60A5FA', order: 2 },
  baseline_diagnosis: { label: '基线诊断中', color: '#38BDF8', order: 3 },
  building_questions: { label: '问题池构建', color: '#818CF8', order: 4 },
  executing: { label: '执行中', color: '#2563EB', order: 5 },
  needs_renewal: { label: '待续费', color: '#F59E0B', order: 6 },
  high_risk: { label: '高风险', color: '#EF4444', order: 7 },
  dispute_handling: { label: '争议处理中', color: '#DC2626', order: 8 },
  completed: { label: '已结束', color: '#6B7280', order: 9 },
}

export const QUESTION_TYPE_MAP: Record<QuestionType, { label: string; color: string }> = {
  brand: { label: '品牌词', color: '#2563EB' },
  location: { label: '地域词', color: '#059669' },
  industry: { label: '行业词', color: '#7C3AED' },
  decision: { label: '决策词', color: '#DC2626' },
  transaction: { label: '成交词', color: '#EA580C' },
  qa: { label: '问答词', color: '#0891B2' },
  comparison: { label: '对比词', color: '#CA8A04' },
  competitor: { label: '竞品词', color: '#BE185D' },
}

export const QUESTION_PRIORITY_MAP: Record<QuestionPriority, { label: string; color: string; desc: string }> = {
  A: { label: 'A 类', color: '#DC2626', desc: '承诺考核，高频监测' },
  B: { label: 'B 类', color: '#F59E0B', desc: '重点观察，中频监测' },
  C: { label: 'C 类', color: '#6B7280', desc: '长尾观察，低频监测' },
}

export interface PlatformMeta {
  code: string
  name: string
  provider: string
  defaultPriority: PlatformPriority
}

export const PLATFORMS: PlatformMeta[] = [
  { code: 'doubao', name: '豆包', provider: '字节跳动', defaultPriority: 'P0' },
  { code: 'deepseek', name: 'DeepSeek', provider: 'DeepSeek', defaultPriority: 'P0' },
  { code: 'qianwen', name: '通义千问', provider: '阿里云', defaultPriority: 'P0' },
  { code: 'wenxin', name: '文心一言', provider: '百度', defaultPriority: 'P0' },
  { code: 'kimi', name: 'Kimi', provider: '月之暗面', defaultPriority: 'P0' },
  { code: 'hunyuan', name: '元宝/混元', provider: '腾讯', defaultPriority: 'P0' },
  { code: 'spark', name: '讯飞星火', provider: '科大讯飞', defaultPriority: 'P1' },
  { code: 'zhipu', name: '智谱清言', provider: '智谱AI', defaultPriority: 'P1' },
  { code: '360brain', name: '360智脑', provider: '360', defaultPriority: 'P1' },
  { code: 'mita', name: '秘塔AI', provider: '秘塔科技', defaultPriority: 'P1' },
  { code: 'nano_ai', name: '纳米AI', provider: '纳米科技', defaultPriority: 'P2' },
  { code: 'tiangong', name: '天工AI', provider: '昆仑万维', defaultPriority: 'P2' },
  { code: 'baichuan', name: '百川AI', provider: '百川智能', defaultPriority: 'P2' },
  { code: 'minimax', name: 'MiniMax', provider: 'MiniMax', defaultPriority: 'P2' },
  { code: 'stepstar', name: '阶跃星辰', provider: '阶跃星辰', defaultPriority: 'P2' },
  { code: 'xiaomi_mimo', name: '小米MiMo', provider: '小米', defaultPriority: 'P2' },
]

export const PLATFORM_MAP = Object.fromEntries(
  PLATFORMS.map((p) => [p.code, p]),
) as Record<string, PlatformMeta>

export const REPORT_TYPE_MAP: Record<ReportType, { label: string; icon: string }> = {
  presale: { label: '售前诊断报告', icon: 'Document' },
  presale_diagnosis: { label: '售前诊断报告', icon: 'Document' },
  management: { label: '管理层汇总', icon: 'PieChart' },
}

export const REPORT_STATUS_MAP: Record<ReportStatus, { label: string; type: string }> = {
  generating: { label: '生成中', type: 'info' },
  draft: { label: '草稿', type: 'warning' },
  intercepted: { label: '已拦截', type: 'danger' },
  published: { label: '已发布', type: 'success' },
  superseded: { label: '已替代', type: 'info' },
  archived: { label: '已归档', type: 'info' },
}

export const ROLE_MAP: Record<RoleType, { label: string; desc: string; isPartner: boolean }> = {
  super_admin: { label: '超级管理员', desc: '系统全权限', isPartner: false },
  manager: { label: '管理员', desc: '业务决策与审核', isPartner: false },
  delivery_manager: { label: '交付负责人', desc: '项目交付与报告复核', isPartner: false },
  operator: { label: '运营', desc: '日常运营与监测', isPartner: false },
  sales: { label: '销售', desc: '客户管理与签约', isPartner: false },
  partner: { label: '合伙人', desc: '城市合伙人主账号', isPartner: true },
  partner_staff: { label: '合伙人员工', desc: '合伙人团队成员', isPartner: true },
  partner_viewer: { label: '合伙人只读', desc: '合伙人查看权限', isPartner: true },
}

export function isPartnerRole(role: RoleType): boolean {
  return ROLE_MAP[role]?.isPartner ?? false
}

export function hasRole(userRole: RoleType, allowedRoles: RoleType[]): boolean {
  if (userRole === 'super_admin') return true
  return allowedRoles.includes(userRole)
}

export const PLATFORM_HEALTH_MAP: Record<PlatformHealth, { label: string; color: string }> = {
  normal: { label: '正常', color: '#10B981' },
  slow_response: { label: '慢响应', color: '#F59E0B' },
  high_failure: { label: '高失败率', color: '#EF4444' },
  degraded: { label: '已降级', color: '#F97316' },
  manual_takeover: { label: '人工接管', color: '#8B5CF6' },
  maintenance: { label: '维护中', color: '#6B7280' },
}

export const TRAINING_STATUS_MAP: Record<TrainingStatus, { label: string; type: string }> = {
  not_trained: { label: '未培训', type: 'info' },
  in_training: { label: '培训中', type: 'warning' },
  passed: { label: '已通过', type: 'success' },
  production_enabled: { label: '正式交付权限', type: 'success' },
}

export const ALERT_SEVERITY_MAP: Record<AlertSeverity, { label: string; color: string }> = {
  info: { label: '信息', color: '#3B82F6' },
  warn: { label: '警告', color: '#F59E0B' },
  error: { label: '错误', color: '#FB923C' },
  critical: { label: '严重', color: '#EF4444' },
}
