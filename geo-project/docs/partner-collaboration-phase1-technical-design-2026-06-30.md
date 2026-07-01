# 合伙人协作交付阶段一技术设计

日期: 2026-06-30

## 1. 目标与边界

阶段一目标是为合伙人协作交付建立可落地的基础模型,不一次性完成所有页面体验。

阶段一必须先处理:

- 项目状态权威口径与旧状态兼容。
- 合伙人角色收敛与 `partner_viewer` 移除。
- 合伙人员工客户级数据范围。
- 合伙人专用 DTO,防止总部隐藏交付信息泄漏。
- 项目启动申请与审批基础模型。
- 诊断报告 `partner_id`、次数/积分预占流水和幂等。
- 客户内部交付负责人移交复用现有服务和审计。

阶段一不处理:

- 完整工作台 UI。
- 所有合伙人套餐管理交互细节。
- 正式启动后的交付执行自动化改造。
- 历史异常财务数据自动推断。

## 2. 现有系统改造点总览

| 领域 | 当前实现 | 阶段一改造点 |
| --- | --- | --- |
| 项目状态 | `ProjectFlowPolicy` 只允许 `pending_start/active/paused/expired`; V147 触发器限制同一集合 | 新增迁移更新触发器/字典;新增 `projectDisplayStatus` 状态解析器 |
| 项目启动 | `ProjectStateGuard.ensureCanStart` 允许合伙人负责人启动,阻止内部用户启动合伙人项目 | 改为合伙人不能启动;总部交付负责人审批后进入配置中,总部再正式启动 |
| 项目创建/更新 | `ProjectService` 创建项目直接进入 `pending_start`,编辑校验未区分合伙人审批阶段 | 合伙人新建项目写入 `draft`;提交、驳回、审批通过、配置完成均由服务层统一流转 |
| 合伙人角色 | 前后端仍硬编码 `partner_viewer` | 新版本移除 `partner_viewer`;迁移前置校验无存量账号 |
| 数据范围 | `partner/partner_staff/partner_viewer` 均按 `partner_id` 范围 | 增加 `company.partner_staff_owner_id`,员工只看分配客户/项目 |
| 套餐与配额 | `package_plan` 统一;`project_channel_allocation` 统计 active 分配 | 区分合伙人套餐;配额拆成 `partner_allocated_quota` 和 `internal_delivery_snapshot` |
| 诊断报告 | `presale_report` 无 `partner_id`,仅有 `created_by` | 增加 `partner_id` 与预占流水;创建接口支持 `request_id` 幂等 |
| 客户移交 | 已有 `/api/companies/{id}/owner-transfer`,服务权限 `delivery.assignment.manage` | 复用现有服务与审计;必要时新增 admin 包装但不复制业务逻辑 |

## 3. 数据库迁移设计

### 3.1 项目状态约束与字典

现有改造点:

- `geo-server/src/main/java/com/huanjing/geo/module/project/service/ProjectFlowPolicy.java`
- `geo-server/src/main/java/com/huanjing/geo/module/project/service/ProjectStateGuard.java`
- `geo-server/src/main/java/com/huanjing/geo/module/project/service/ProjectService.java`
- `geo-server/src/main/java/com/huanjing/geo/module/workbench/service/WorkbenchService.java`
- `geo-server/src/main/java/com/huanjing/geo/module/delivery/service/DeliveryDashboardService.java`
- `geo-server/src/main/java/com/huanjing/geo/module/system/service/DashboardService.java`
- `geo-server/src/main/resources/db/migration/V147__project_status_pending_start_expired.sql`
- 前端 `geo-web/src/types/index.ts` 中 `ProjectStatus`
- 前端 `geo-web/src/utils/constants.ts` 中项目状态映射
- 前端项目列表、详情、合伙人项目、统计工作台中所有 `pending_start/active/paused/expired` 判断

新增 Flyway 迁移:

- Drop 并重建 `trg_project_before_insert`、`trg_project_before_update`。
- 项目状态允许集合更新为:
  - `draft`
  - `submitted`
  - `rejected`
  - `approved_pending_setup`
  - `setup_ready`
  - `active`
  - `paused`
  - `completed`
  - `archived`
  - `cancelled`
- `cancelled` 本期无入口,仅预留。
- 阶段一触发器继续允许 `expired` 写入,避免打断当前 `ProjectService` 过期任务和批量更新逻辑。
- 服务层禁止合伙人新流程主动流转到 `expired`;旧直营/存量过期逻辑仍可写 `expired`,并由 `projectDisplayStatus` 展示为 `archived`。
- 后续阶段若完成全部过期逻辑迁移,再移除 `expired` 写入兼容。
- `sys_dict_item` 新增/更新上述 `project_status` 字典。

验收:

- 迁移后新状态可写入。
- 当前 `expired` 定时/批量逻辑不因触发器变更中断。
- 旧项目读取不报错。
- 旧 `expired` 项目可通过 `projectDisplayStatus` 展示为 `archived`。
- 所有状态展示不再由前端页面自行硬编码判断。

### 3.2 项目启动申请表

新增表 `project_start_request`:

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `project_id` | BIGINT NOT NULL | 项目 |
| `company_id` | BIGINT NOT NULL | 客户 |
| `partner_id` | BIGINT NOT NULL | 合伙人 |
| `applicant_user_id` | BIGINT NOT NULL | 申请人,只能合伙人负责人 |
| `status` | VARCHAR(32) NOT NULL | `submitted/approved/rejected/cancelled` |
| `active_submitted_project_id` | BIGINT GENERATED | `IF(status='submitted', project_id, NULL)` |
| `request_no` | VARCHAR(64) NOT NULL | 申请编号 |
| `submitted_at` | DATETIME NOT NULL | 提交时间 |
| `reviewed_by` | BIGINT NULL | 审批人 |
| `reviewed_at` | DATETIME NULL | 审批时间 |
| `reject_reason_code` | VARCHAR(64) NULL | 驳回原因 |
| `reject_reason_text` | VARCHAR(500) NULL | 驳回说明 |
| `assigned_internal_owner_id` | BIGINT NULL | 审批时指定/沿用的内部交付人员 |
| `points_required_snapshot` | DECIMAL(18,2) NULL | 首单消耗积分 |
| `discount_rate_snapshot` | DECIMAL(10,4) NULL | 折扣系数 |
| `package_snapshot_json` | JSON NULL | 客户套餐快照 |
| `partner_allocated_quota_json` | JSON NULL | 合伙人可见配额快照 |
| `internal_delivery_snapshot_json` | JSON NULL | 总部隐藏渠道快照 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

索引:

- `idx_psr_project_status (project_id, status, id)`
- `idx_psr_partner_status (partner_id, status, submitted_at)`
- `idx_psr_company_status (company_id, status, submitted_at)`
- `uk_psr_active_submitted_project (active_submitted_project_id)`

约束:

- 同一项目同一时间只能有一个 `submitted` 申请。
- MySQL 通过生成列 `active_submitted_project_id = IF(status = 'submitted', project_id, NULL)` + 唯一索引实现该约束;非 `submitted` 状态生成 `NULL`,允许保留多条历史申请。
- 取消申请只更新申请状态为 `cancelled`,项目回到 `draft`,不取消项目。

影响代码:

- 新增 entity/mapper/service/controller。
- `ProjectService` 创建合伙人项目时写 `draft`,不再默认直接进入可启动流程。
- `ProjectStateGuard.ensureCanStart` 改为禁止合伙人启动,允许总部在 `setup_ready` 时启动。

### 3.3 项目配额快照

方案 A: 复用并扩展 `project_channel_allocation`。

需新增字段:

- `allocation_status`: `draft/submitted/locked/released`
- `start_request_id`
- `locked_at`
- `released_at`
- `snapshot_json`

方案 B: 新增 `project_quota_snapshot`。

建议阶段一采用方案 B,避免旧 `project_channel_allocation` 的 active 统计语义污染新审批语义。

新增表 `project_quota_snapshot`:

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `project_id` | BIGINT NOT NULL | 项目 |
| `company_id` | BIGINT NOT NULL | 客户 |
| `start_request_id` | BIGINT NULL | 启动申请 |
| `status` | VARCHAR(32) NOT NULL | `draft/submitted/locked/released` |
| `partner_allocated_quota_json` | JSON NOT NULL | Agent 官网、自媒体、核心问题 |
| `internal_delivery_snapshot_json` | JSON NULL | 论坛/平台网站、行业资讯站、权威媒体 |
| `locked_at` | DATETIME NULL | 审批通过锁定时间 |
| `released_at` | DATETIME NULL | 驳回/取消释放时间 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

规则:

- 草稿不占用额度。
- 提交启动申请时按实时剩余额度校验并占用 `partner_allocated_quota`。
- 驳回/取消申请释放占用。
- 审批通过后状态 `locked`,保存快照。
- `internal_delivery_snapshot` 不参与客户剩余额度递减,每个审批通过项目按套餐内部配置保存同额快照。

影响代码:

- `ProjectDistributionChannelAllocationService` 需要拆出合伙人配额计算路径。
- `ProjectChannelAllocationMapper.sumActiveAllocatedByCompanyAndChannel` 不能作为合伙人审批占用的唯一口径。
- 合伙人 DTO 只返回 `partner_allocated_quota`。
- 总部 DTO 可返回两层快照。

### 3.4 合伙人套餐字段

现有可扩展 `package_plan`,阶段一建议先扩字段而非拆表。

新增字段:

- `audience_type`: `internal/partner`
- `package_status`: `draft/active/inactive`
- `partner_points`
- `partner_visible_config_json`
- `internal_delivery_config_json`
- `deleted_at`
- `deleted_by`

接口约束:

- `PUT /api/admin/partner-packages/{id}` 只允许编辑 `draft` 且未绑定的套餐。
- 已上架或已被客户绑定的套餐不允许原地修改核心配置。
- 业务调整通过新增套餐承接。
- 已绑定套餐禁止删除,只能下架/停用。

影响代码:

- `PackagePlanService`
- `PackageConfig.vue`
- `CompanyPackageBindingService`
- 客户详情套餐绑定页面

### 3.5 客户套餐绑定与快照

现有 `company_package_binding` 可复用。

需补充:

- `package_snapshot_json`
- `partner_visible_snapshot_json`
- `internal_delivery_snapshot_json`
- `locked_at`
- `locked_by_project_id`
- `locked_by_approval_id`

规则:

- 客户首单消耗前可修改绑定套餐。
- 首单消耗后锁定,总部也不做套餐变更。
- 后续项目继续使用锁定快照。

### 3.6 合伙人员工分配

新增字段:

- `company.partner_staff_owner_id`

影响代码:

- `InternalScopeService`
- `CompanyService.page/detail`
- `BrandService` / 品牌查询链路
- `ProjectService.page/detail`
- 拓词/核心问题相关查询

规则:

- 合伙人负责人看本合伙人全部客户/项目。
- 合伙人交付员工只能看 `company.partner_staff_owner_id = 当前用户` 的客户及下游数据。
- 新建客户默认不分配给交付员工,由合伙人负责人手动分配。
- 合伙人负责人可在客户详情分配/取消分配本合伙人的唯一交付员工。
- 总部可通过后台兜底调整分配。
- 分配变更写活动日志,记录原交付员工、新交付员工、操作人和原因。

### 3.7 诊断报告归属与预占流水

现有改造点:

- `PresaleReport` 当前无 `partner_id`。
- `PresaleReportService.createReport` 当前只按用户权限创建,无合伙人次数/积分逻辑。

`presale_report` 新增字段:

- `partner_id`
- `partner_presale_charge_type`: `free_quota/points`
- `partner_presale_points`
- `partner_presale_quota_txn_id`
- `partner_presale_points_txn_id`
- `request_id`
- `request_hash`
- `request_payload_snapshot_json`

索引:

- `idx_presale_report_partner_created_at (partner_id, created_at)`
- `uk_presale_report_partner_request (partner_id, request_id)`

新增表 `partner_presale_report_quota_txn`:

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `partner_id` | BIGINT NOT NULL | 合伙人 |
| `request_id` | VARCHAR(128) NOT NULL | 幂等键 |
| `request_hash` | VARCHAR(128) NOT NULL | 规范化请求摘要 |
| `request_payload_snapshot_json` | JSON NOT NULL | 创建请求快照 |
| `report_id` | BIGINT NULL | 报告 |
| `biz_type` | VARCHAR(32) NOT NULL | `free_quota/points` |
| `points_amount` | DECIMAL(18,2) NULL | 超额积分 |
| `quota_amount` | INT NULL | 免费次数 |
| `status` | VARCHAR(32) NOT NULL | `reserved/confirmed/refunded/manual_review` |
| `failure_code` | VARCHAR(64) NULL | 失败码 |
| `failure_message` | VARCHAR(512) NULL | 失败说明 |
| `related_points_txn_id` | BIGINT NULL | 积分流水 |
| `created_by` | BIGINT NOT NULL | 创建人 |
| `created_at` | DATETIME | 创建时间 |
| `confirmed_at` | DATETIME NULL | 确认时间 |
| `refunded_at` | DATETIME NULL | 回补时间 |

唯一约束:

- `uk_partner_presale_request (partner_id, request_id)`

## 4. 后端接口契约

### 4.1 DTO 分层原则

所有合伙人接口使用 Partner DTO,总部接口使用 Admin DTO。

禁止从合伙人 DTO 返回:

- `internal_delivery_snapshot`
- 论坛/平台网站配置
- 行业资讯站配置
- 权威媒体配置
- 指纹浏览器环境
- 自媒体账号绑定信息
- Cookie/登录态
- 本地助手状态
- 内部交付人员的敏感配置

### 4.2 项目列表/详情

合伙人 DTO: `PartnerProjectVO`

返回字段:

- `id`
- `companyId`
- `companyName`
- `brandId`
- `brandName`
- `projectName`
- `projectDisplayStatus`
- `projectDisplayStatusLabel`
- `latestStartRequestStatus`
- `partnerAllocatedQuota`
- `canEdit`
- `canSubmitStartRequest`
- `rejectReason`

禁止字段:

- `internalDeliverySnapshot`
- `browserEnvironment*`
- `selfMediaAccount*`
- `contentExecution*`
- 隐藏渠道配置

总部 DTO: `AdminProjectVO`

可返回:

- `projectDisplayStatus`
- `rawProjectStatus`
- `latestStartRequest`
- `partnerAllocatedQuota`
- `internalDeliverySnapshot`
- `assignedInternalOwner`
- 审批快照

现有改造点:

- `ProjectService.page/detail`
- `ProjectService.create/update`
- `WorkbenchService`
- `DeliveryDashboardService`
- `DashboardService`
- `geo-web/src/views/partner/MyProjects.vue`
- `geo-web/src/views/admin/project/ProjectList.vue`
- `geo-web/src/views/admin/project/ProjectDetail.vue`

### 4.3 套餐与配额接口

合伙人套餐 DTO: `PartnerPackageVO`

返回字段:

- `id`
- `name`
- `packageStatus`
- `partnerPoints`
- `visibleChannels`
- `agentOfficialSiteQuota`
- `selfMediaQuota`
- `coreQuestionQuota`
- `publishFrequency`

禁止字段:

- `internalDeliveryConfig`
- 论坛/平台网站额度与频次
- 行业资讯站额度与频次
- 权威媒体额度与频次
- 后台执行参数

总部套餐 DTO: `AdminPartnerPackageVO`

可返回:

- `partnerVisibleConfig`
- `internalDeliveryConfig`
- `boundCompanyCount`
- `createdBy`
- `updatedBy`
- 上下架状态

合伙人项目配额 DTO: `PartnerProjectQuotaVO`

返回字段:

- `packageId`
- `packageName`
- `totalVisibleQuota`
- `usedVisibleQuota`
- `remainingVisibleQuota`
- `currentProjectAllocatedQuota`

总部项目配额 DTO: `AdminProjectQuotaSnapshotVO`

可返回:

- `partnerAllocatedQuota`
- `internalDeliverySnapshot`
- `quotaSnapshotStatus`
- `lockedAt`
- `releasedAt`
- `startRequestId`

### 4.4 项目状态解析

新增服务: `ProjectDisplayStatusResolver`

输入:

- `Project`
- latest `ProjectStartRequest`

输出:

- `projectDisplayStatus`
- `label`
- `editable`
- `submittable`

映射规则:

| 条件 | Display |
| --- | --- |
| 合伙人项目 `pending_start` 且无申请 | `draft` |
| 最新申请 `submitted` | `submitted` |
| 最新申请 `rejected` | `rejected` |
| 最新申请 `cancelled` 且项目未审批 | `draft` |
| 最新申请 `approved`,项目未 active | `approved_pending_setup` |
| 项目 `active` | `active` |
| 项目 `paused` | `paused` |
| 项目 `expired` | `archived` |

验收:

- 列表、详情、统计、工作台、导出使用同一 resolver。

### 4.5 启动申请接口

合伙人提交:

`POST /api/partner/projects/{id}/start-requests`

请求:

- `requestId`
- `remark`

校验:

- 当前用户是合伙人负责人。
- 项目属于当前合伙人。
- 客户已绑定合伙人套餐。
- 当前项目可提交。
- 草稿配额不超过实时剩余额度。
- 若可能成为首单,合伙人积分充足。
- 不存在同项目 `submitted` 申请。

响应 Partner DTO:

- `requestId`
- `requestNo`
- `status`
- `projectDisplayStatus`
- `partnerAllocatedQuota`

Partner DTO 禁止返回:

- `internalDeliverySnapshot`
- `assignedInternalOwner`
- 总部审批备注
- 积分扣减内部流水明细

取消申请:

`POST /api/partner/projects/{id}/start-requests/{requestId}/cancel`

规则:

- 仅可取消 `submitted` 申请。
- 仅取消申请,不取消项目。
- 释放申请占用额度。
- 项目展示回 `draft`。

总部审批:

`POST /api/admin/project-start-requests/{id}/approve`

请求:

- `assignedInternalOwnerId` 可选;客户未分配或原负责人停用时必填。
- `reviewRemark`

校验:

- 当前用户有审批权限。
- 客户已有负责人且有效时自动沿用。
- 客户负责人停用时阻断,要求先客户移交。
- 再次校验积分。

响应 Admin DTO:

- `id`
- `requestNo`
- `projectId`
- `companyId`
- `partnerId`
- `applicantUserId`
- `status`
- `projectDisplayStatus`
- `assignedInternalOwner`
- `pointsRequiredSnapshot`
- `discountRateSnapshot`
- `packageSnapshot`
- `partnerAllocatedQuota`
- `internalDeliverySnapshot`
- `reviewedBy`
- `reviewedAt`

`POST /api/admin/project-start-requests/{id}/reject`

请求:

- `rejectReasonCode`
- `rejectReasonText`

### 4.6 诊断报告接口

合伙人创建:

`POST /api/partner/presale-report`

请求必须包含:

- `requestId`
- 报告业务参数

幂等:

- `partner_id + request_id` 唯一。
- 服务端对规范化后的请求参数计算 `request_hash`。
- 重复请求的 `request_hash` 一致时返回已有报告/任务状态。
- 重复请求的 `request_hash` 不一致时返回幂等冲突。
- `request_payload_snapshot_json` 用于人工排查和异常对账。

合伙人 DTO:

- `reportId`
- `generationStatus`
- `quotaChargeType`
- `remainingFreeQuota`
- `pointsCharged`

总部 DTO:

- 合伙人信息
- 预占流水状态
- 积分/次数扣减详情
- 异常处理状态

### 4.7 客户移交接口

优先复用现有:

- `POST /api/companies/{id}/owner-transfer`

权限:

- `delivery.assignment.manage`
- 本期默认交付负责人和超级管理员可操作。
- 管理者若要操作,必须调整权限种子和断言。

如新增包装:

- `POST /api/admin/companies/{id}/owner-transfer`

要求:

- 调用同一 `CompanyService.transferOwner`。
- 复用同一活动日志。
- 不复制业务逻辑。

### 4.8 合伙人员工分配接口

合伙人负责人分配:

`POST /api/partner/companies/{id}/staff-owner`

请求:

- `partnerStaffUserId`
- `reason`

规则:

- 当前用户必须是合伙人负责人。
- 客户必须属于当前合伙人。
- `partnerStaffUserId` 必须是当前合伙人下的启用状态交付员工。
- 当前阶段合伙人最多一个交付员工,该接口仍按单字段分配,为后续多人扩展保留入口。
- 允许传空值取消分配。

响应 Partner DTO:

- `companyId`
- `partnerStaffOwnerId`
- `partnerStaffOwnerName`

总部兜底分配:

`POST /api/admin/partner-companies/{id}/staff-owner`

规则:

- 需要合伙人客户管理权限。
- 调整同一字段 `company.partner_staff_owner_id`。
- 复用同一活动日志记录。

## 5. 状态机设计

### 5.1 三层状态

| 层 | 字段 | 用途 |
| --- | --- | --- |
| 项目业务主状态 | `project.status` | 合伙人项目从草稿、申请、审批、配置到正式启动后的主状态 |
| 申请状态 | `project_start_request.status` | 合伙人提交/审批过程 |
| 展示状态 | `projectDisplayStatus` | 所有列表、详情、统计、工作台统一口径 |

### 5.2 测试矩阵

| 场景 | project.status | request.status | display |
| --- | --- | --- | --- |
| 合伙人新项目 | `draft` 或旧 `pending_start` | 无 | `draft` |
| 已提交 | `submitted` 或旧 `pending_start` | `submitted` | `submitted` |
| 已驳回 | `rejected` 或旧 `pending_start` | `rejected` | `rejected` |
| 取消申请 | `draft` 或旧 `pending_start` | `cancelled` | `draft` |
| 审批通过待配置 | `approved_pending_setup` 或旧 `pending_start` + approved | `approved` | `approved_pending_setup` |
| 配置完成 | `setup_ready` | `approved` | `setup_ready` |
| 已启动 | `active` | 任意历史 | `active` |
| 已暂停 | `paused` | 任意历史 | `paused` |
| 旧过期 | `expired` | 任意历史 | `archived` |

阶段一状态权威口径:

- 新合伙人流程写入新 `project.status`,作为项目业务主状态。
- `project_start_request.status` 作为申请审计状态和审批记录,不替代项目业务主状态。
- `projectDisplayStatus` 由 `project.status` + latest `project_start_request.status` 统一解析,作为前端列表、详情、统计、工作台、导出的唯一展示状态。
- 旧项目保留旧值,通过 resolver 兼容。
- 合伙人侧接口主要暴露 `projectDisplayStatus`,不要求前端自行解释 `rawProjectStatus`。
- 总部侧接口可返回 `rawProjectStatus` 和申请状态,用于排查与审批。

状态写入规则:

- 合伙人项目创建写 `draft`。
- 合伙人负责人提交启动申请后写 `submitted`。
- 总部驳回后写 `rejected`。
- 合伙人修改驳回项目后回到 `draft`。
- 合伙人取消已提交申请后回到 `draft`。
- 总部审批通过后写 `approved_pending_setup`。
- 总部完成账号/指纹浏览器/本地助手准备后写 `setup_ready`。
- 总部正式启动后写 `active`。
- 暂停、完成、归档分别写 `paused/completed/archived`。
- 旧逻辑写入的 `expired` 继续兼容,展示为 `archived`。

## 6. 权限与数据范围

### 6.1 角色

新版本合伙人门户角色:

- `partner`: 合伙人负责人。
- `partner_staff`: 合伙人交付员工。

移除:

- `partner_viewer`。

改造点:

- `geo-web/src/router/partner.ts`
- `geo-web/src/types/index.ts`
- `geo-web/src/utils/constants.ts`
- `geo-web/src/layouts/components/TopBar.vue`
- `geo-web/src/views/profile/ProfileCenter.vue`
- `geo-server/.../CurrentUserService.java`
- `geo-server/.../UserAdminService.java`
- 权限种子和角色字典迁移

### 6.2 数据范围

合伙人负责人:

- `partner_id = 当前用户.partner_id`

合伙人交付员工:

- `company.partner_id = 当前用户.partner_id`
- `company.partner_staff_owner_id = 当前用户.id`

需统一接入:

- 客户列表/详情。
- 品牌列表/详情。
- 项目列表/详情。
- 核心问题/拓词管理。
- 项目启动申请。

### 6.3 DTO 防泄漏

后端不允许通过合伙人接口返回隐藏字段。前端隐藏不是安全边界。

验收:

- 合伙人项目详情响应不包含 `internalDeliverySnapshot`。
- 合伙人套餐详情响应不包含论坛、行业资讯站、权威媒体配置。
- 合伙人接口不返回浏览器环境、自媒体账号绑定、Cookie、local-agent 信息。

## 7. 阶段一测试清单

### 7.1 数据库迁移

- 项目状态触发器允许新状态。
- 项目状态触发器阶段一继续允许 `expired` 写入。
- 状态字典包含新状态。
- `partner_viewer` 存量用户前置校验通过;如存在则迁移阻断。
- 新表索引和唯一约束生效。
- `project_start_request.active_submitted_project_id` 生成列唯一索引能阻止并发双 `submitted`。

### 7.2 状态解析

- 所有旧状态映射符合表格。
- 合伙人列表、详情、工作台、统计使用同一 `projectDisplayStatus`。
- 旧 `expired` 展示为 `archived`。

### 7.3 权限与数据范围

- 合伙人负责人可看本合伙人全部客户/项目。
- 合伙人交付员工只能看分配客户/项目。
- 合伙人负责人可分配/取消分配交付员工。
- 总部可兜底调整合伙人员工分配。
- 分配变更写活动日志。
- `partner_viewer` 不能进入合伙人路由。
- 用户管理不能新建/分配 `partner_viewer`。

### 7.4 DTO 防泄漏

- 合伙人套餐 DTO 不含隐藏渠道。
- 合伙人项目 DTO 不含 `internal_delivery_snapshot`。
- 合伙人接口不含浏览器环境、自媒体账号绑定、本地助手字段。

### 7.5 启动申请

- 提交时实时校验额度。
- 重复提交被拒绝或幂等返回。
- 取消申请释放额度,项目回到 `draft`。
- 驳回释放额度并允许修改重提。
- 审批通过锁定配额快照。
- 原内部交付负责人停用时审批阻断并提示客户移交。

### 7.6 诊断报告

- `partner_id + request_id` 幂等。
- 相同 `request_id` 且 `request_hash` 不一致时返回幂等冲突。
- `request_payload_snapshot_json` 可用于人工核验。
- 并发请求不重复扣免费次数/积分。
- 成功后 `reserved -> confirmed`。
- 系统失败后 `reserved -> refunded`。
- 超时进入 `manual_review` 且额度/积分继续冻结。
- 人工重跑不重复预占。

### 7.7 客户移交

- 复用 `CompanyService.transferOwner`。
- 权限使用 `delivery.assignment.manage`。
- 活动日志记录原负责人、新负责人、操作人、原因。
- 移交后后续项目审批自动沿用新负责人。
