const BUSINESS_ERROR_MESSAGES: Array<[RegExp, string]> = [
  [/^Customer has no active package binding$/i, '客户尚未绑定有效套餐，请先在客户详情中绑定合伙人套餐'],
  [/^Customer already has active package binding$/i, '客户已绑定有效套餐'],
  [/^Customer package binding status changed/i, '客户套餐绑定状态已变化，请刷新后重试'],
  [/^Insufficient customer balance$/i, '客户积分余额不足'],
  [/^No permission/i, '当前账号没有访问该功能的权限'],
  [/^Partner users cannot access/i, '该功能仅总部交付人员可用，合伙人账号不可访问'],
  [/^Partners cannot access/i, '该功能仅总部交付人员可用，合伙人账号不可访问'],
  [/^Only partner owner can submit project start request$/i, '仅合伙人负责人可以提交项目启动申请'],
  [/^Partner staff cannot create presale reports$/i, '交付员工不能创建诊断报告，请由合伙人负责人操作'],
  [/^Partner staff cannot access presale reports$/i, '交付员工不能查看诊断报告，请由合伙人负责人查看'],
  [/^Only one partner staff account is allowed$/i, '当前合伙人只能创建一个交付员工'],
  [/^Only partner owner can manage partner staff$/i, '仅合伙人负责人可以管理员工'],
  [/^Only partner owner can manage customer package$/i, '仅合伙人负责人可以管理客户套餐'],
  [/^Partner staff owner is unchanged$/i, '当前客户已分配给该交付员工，无需重复分配'],
  [/^Partner staff must be active and belong to this partner$/i, '请选择当前合伙人下启用状态的交付员工'],
  [/^Partner account missing partner_id binding$/i, '当前账号未绑定合伙人信息，请联系管理员处理'],
  [/^Partner account not found$/i, '合伙人积分账户不存在，请联系管理员处理'],
  [/^Partner not found$/i, '合伙人不存在或已删除'],
  [/^Partner staff not found$/i, '交付员工不存在或不属于当前合伙人'],
  [/^Partner is not active$/i, '合伙人已停用，无法继续操作'],
  [/^partner_code already exists$/i, '合伙人编号已存在，请刷新后重试'],
  [/^username already exists$/i, '账号已存在，请更换后重试'],
  [/^Project not found$/i, '项目不存在或已删除'],
  [/^Company not found$/i, '客户不存在或已删除'],
  [/^Brand not found$/i, '品牌不存在或已删除'],
  [/^Start request not found$/i, '项目启动申请不存在'],
  [/^Start request status changed/i, '项目启动申请状态已变化，请刷新后重试'],
  [/^Only partner project can submit start request$/i, '仅合伙人项目可以提交启动申请'],
  [/^Project is not submittable in current status$/i, '当前项目状态不能提交启动申请，请刷新后确认'],
  [/^Customer must bind a partner package before project submission$/i, '客户创建项目前必须先绑定合伙人套餐'],
  [/^Bound package plan not found$/i, '客户绑定的套餐不存在或已停用'],
  [/^Partner package points are not configured$/i, '合伙人套餐积分未配置，请先完善套餐配置'],
  [/^Partner points are insufficient for first project approval$/i, '合伙人积分不足，无法通过首个项目启动申请'],
  [/^Partner presale report request already exists/i, '诊断报告申请已提交，请刷新后查看处理结果'],
  [/^Partner presale report requestId is required$/i, '诊断报告申请编号不能为空'],
  [/^Invalid presale report request payload$/i, '诊断报告申请参数不正确，请刷新后重试'],
  [/^Image folder not found$/i, '图片文件夹不存在或已删除'],
  [/^folderId required$/i, '请选择图片文件夹'],
  [/^Invalid folder status$/i, '图片文件夹状态不正确'],
  [/^Recharge order not found$/i, '充值记录不存在或已处理'],
  [/^Only pending recharge order can be audited$/i, '仅待处理的充值记录可以审核'],
  [/^Recharge order has expired$/i, '充值记录已过期，请重新提交资料'],
  [/^Reject reason is required$/i, '驳回时必须填写原因'],
  [/^Invalid audit action$/i, '审核操作不正确'],
  [/^Recharge amount must be positive$/i, '充值积分必须大于 0'],
  [/^Adjust amount cannot be zero$/i, '调整积分不能为 0'],
  [/^Balance cannot be negative$/i, '调整后积分余额不能为负数'],
]

export function normalizeErrorMessage(message: unknown, fallback: string) {
  const text = typeof message === 'string' ? message.trim() : ''
  if (!text) return fallback
  const matched = BUSINESS_ERROR_MESSAGES.find(([pattern]) => pattern.test(text))
  return matched?.[1] || text
}

export function errorMessage(err: unknown, fallback: string) {
  const data = (err as any)?.response?.data
  const status = (err as any)?.response?.status ?? (err as any)?.status
  const code = (err as any)?.code ?? data?.code
  if (status === 401 || code === 401) {
    const message = data?.message && data.message !== 'Unauthorized'
      ? data.message
      : '登录状态已失效，请重新登录后重试'
    return normalizeErrorMessage(message, fallback)
  }
  return normalizeErrorMessage(data?.message || data?.msg || (err as any)?.message, fallback)
}
