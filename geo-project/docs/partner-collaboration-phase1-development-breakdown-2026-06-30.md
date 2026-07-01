# 合伙人协作交付阶段一研发任务拆分

日期: 2026-06-30

关联文档:

- `docs/partner-collaboration-business-plan-2026-06-30.md`
- `docs/partner-collaboration-phase1-technical-design-2026-06-30.md`

## 1. 拆分原则

阶段一先完成基础模型和安全边界,再补业务流程页面。研发顺序按依赖推进:

1. 数据库迁移与状态 resolver。
2. 权限与数据范围。
3. 合伙人/总部 DTO 分层。
4. 启动申请、审批、配额锁定。
5. 诊断报告幂等与次数/积分流水。
6. 客户移交复用与合伙人员工分配。

所有合伙人接口必须使用 Partner DTO,不能复用总部 DTO 后由前端隐藏字段。

测试要求:

- T15 是最终集成回归任务。
- 各任务自己的单元测试、集成测试和关键回归用例必须随任务交付,不能集中留到 T15。
- 高风险点必须在对应任务内完成测试,包括生成列唯一索引、状态 resolver、数据范围、DTO 防泄漏、启动申请并发、诊断报告幂等和失败回补。

## 2. 里程碑

| 里程碑 | 内容 | 退出标准 |
| --- | --- | --- |
| M1 | 迁移与状态主线 | 新状态可写;旧状态兼容;`projectDisplayStatus` 可统一返回 |
| M2 | 角色和数据范围 | `partner_viewer` 移除;合伙人员工只看分配客户 |
| M3 | DTO 防泄漏 | 合伙人项目/套餐/配额/报告接口不返回隐藏交付字段 |
| M4 | 启动申请审批 | 合伙人提交申请;总部审批/驳回;配额占用/释放/锁定 |
| M5 | 诊断报告额度 | 报告幂等;免费次数/积分预占;失败回补 |
| M6 | 移交与分配闭环 | 内部负责人移交复用;合伙人员工分配可操作且有审计 |

## 3. 任务清单

### T00 迁移预检与发布检查

依赖: 无

改造点:

- 新增迁移预检 SQL 或发布检查脚本
- 发布清单

任务:

- 检查当前 MySQL 版本。
- 检查当前 MySQL 是否支持生成列唯一索引。
- 确认生成列使用 `STORED` 还是 `VIRTUAL`。
- 统计 `partner_viewer` 存量账号数量。
- 统计项目旧状态分布,包括 `pending_start/active/paused/expired`。
- 统计当前 `expired` 项目数量和最近过期任务写入情况。
- 输出迁移前检查结果,作为发布前置条件。

验收:

- 发布前能明确判断是否可使用生成列唯一索引方案。
- 若存在 `partner_viewer` 存量账号,发布检查阻断并要求人工确认。
- 旧状态分布和 `expired` 数量有记录,便于迁移后对账。

### T01 数据库迁移: 项目状态与字典

依赖: T00

改造点:

- `geo-server/src/main/resources/db/migration/V147__project_status_pending_start_expired.sql`
- 新增 Flyway 迁移文件
- `sys_dict_item` 项目状态字典

任务:

- 重建项目状态触发器,允许 `draft/submitted/rejected/approved_pending_setup/setup_ready/active/paused/completed/archived/cancelled/expired`。
- 阶段一继续允许 `expired` 写入,避免影响当前过期任务。
- 更新项目状态字典。
- 增加 `partner_viewer` 存量校验迁移;若存在存量账号则阻断并提示人工处理。

验收:

- 新状态可插入和更新。
- 当前 `expired` 写入路径不被触发器拦截。
- 字典接口能返回新状态。
- 存量 `partner_viewer` 为 0 时迁移通过;非 0 时迁移失败并给出明确错误。

### T02 数据库迁移: 启动申请、配额快照、诊断流水

依赖: T01

改造点:

- 新增 `project_start_request`
- 新增 `project_quota_snapshot`
- 扩展 `presale_report`
- 新增 `partner_presale_report_quota_txn`
- 扩展 `package_plan`
- 扩展 `company_package_binding`
- 扩展 `company`

任务:

- 建立 `project_start_request`,含生成列 `active_submitted_project_id`。
- 使用 MySQL `GENERATED ALWAYS AS (IF(status = 'submitted', project_id, NULL))` 生成列语法;根据当前 MySQL 版本确认使用 `STORED` 或 `VIRTUAL`。
- 对 `active_submitted_project_id` 建唯一索引。
- 建立 `project_quota_snapshot`。
- `presale_report` 增加 `partner_id/request_id/request_hash/request_payload_snapshot_json` 等字段。
- 建立 `partner_presale_report_quota_txn`,并对 `partner_id + request_id` 建唯一索引。
- `company` 增加 `partner_staff_owner_id`。
- 套餐与客户套餐绑定增加合伙人可见配置、隐藏交付配置和锁定快照字段。

验收:

- 并发插入同一项目两个 `submitted` 申请时,数据库唯一索引只允许一条成功。
- 唯一键冲突不能直接返回数据库异常,后续服务层需转成业务错误或幂等返回。
- `partner_presale_report_quota_txn` 可按 `partner_id + request_id` 幂等定位。

### T03 状态常量与 resolver

依赖: T01, T02

改造点:

- `ProjectFlowPolicy.java`
- `ProjectStateGuard.java`
- `ProjectService.java`
- 新增 `ProjectDisplayStatusResolver`
- `WorkbenchService.java`
- `DeliveryDashboardService.java`
- `DashboardService.java`
- `geo-web/src/types/index.ts`
- `geo-web/src/utils/constants.ts`

任务:

- 定义项目业务主状态枚举。
- 实现 `ProjectDisplayStatusResolver`,统一解析 `project.status + latest project_start_request.status`。
- 将列表、详情、统计、工作台、导出切到 resolver 输出。
- 合伙人新流程禁止主动写入 `expired`。
- `ensureCanStart` 改为合伙人不能启动,总部仅可在 `setup_ready` 启动。
- 随任务交付 resolver 单元测试和旧状态兼容测试。

验收:

- 旧 `pending_start` 合伙人项目无申请时展示 `draft`。
- 旧 `expired` 展示 `archived`。
- `submitted/rejected/approved_pending_setup/setup_ready` 在前后端状态常量中一致。
- 后端工作台统计不再硬编码只按旧状态计算。

### T04 `partner_viewer` 移除

依赖: T01

改造点:

- `geo-web/src/router/partner.ts`
- `geo-web/src/types/index.ts`
- `geo-web/src/utils/constants.ts`
- `geo-web/src/layouts/components/TopBar.vue`
- `geo-web/src/views/profile/ProfileCenter.vue`
- `CurrentUserService.java`
- `UserAdminService.java`
- 权限种子、角色字典、相关断言测试

任务:

- 从合伙人门户路由角色中移除 `partner_viewer`。
- 后端当前用户角色识别不再把 `partner_viewer` 当作合伙人角色。
- 用户管理不允许创建或分配 `partner_viewer`。
- 权限种子和字典移除新版本入口。

验收:

- `partner_viewer` 无法进入合伙人门户。
- 新建/编辑用户角色列表不出现 `partner_viewer`。
- 若历史数据存在 `partner_viewer`,迁移已在 T01 阻断。

### T05 合伙人员工数据范围

依赖: T02, T04

改造点:

- `InternalScopeService`
- `CompanyService`
- `BrandService`
- `ProjectService`
- 拓词/核心问题查询服务
- 相关 Mapper 查询

任务:

- 合伙人负责人范围: `partner_id = 当前用户.partner_id`。
- 合伙人交付员工范围: `partner_id = 当前用户.partner_id` 且 `company.partner_staff_owner_id = 当前用户.id`。
- 将客户、品牌、项目、核心问题、启动申请查询统一接入员工范围。
- T05 测试可通过数据库种子或测试夹具设置 `company.partner_staff_owner_id`;真实分配/取消分配接口在 T13 验收。
- 随任务交付数据范围测试。

验收:

- 合伙人负责人可看本合伙人全部数据。
- 合伙人交付员工只能看分配给自己的客户及下游品牌/项目/核心问题。
- 合伙人交付员工不能看诊断报告。
- 其他合伙人、总部直营数据不可见。

### T06 合伙人和总部 DTO 分层

依赖: T03, T05

改造点:

- 项目列表/详情接口
- 套餐/配额接口
- 启动申请接口
- 诊断报告接口
- 前端 API 类型

任务:

- 定义 `PartnerProjectVO` 与 `AdminProjectVO`。
- 定义 `PartnerPackageVO` 与 `AdminPartnerPackageVO`。
- 定义 `PartnerProjectQuotaVO` 与 `AdminProjectQuotaSnapshotVO`。
- 定义诊断报告 Partner/Admin DTO。
- 合伙人接口禁止返回隐藏渠道、指纹浏览器、自媒体账号绑定、本地助手、Cookie、内部交付敏感配置。
- 随任务交付 DTO 防泄漏响应字段测试。

验收:

- 合伙人项目详情响应不含 `internalDeliverySnapshot`。
- 合伙人套餐响应不含论坛/平台网站、行业资讯站、权威媒体配置。
- 合伙人接口不返回浏览器环境、自媒体账号绑定、Cookie、本地助手字段。
- 总部接口可查看完整审批快照和隐藏交付快照。

### T07 合伙人套餐和客户套餐绑定

依赖: T02, T06

改造点:

- `PackagePlanService`
- `CompanyPackageBindingService`
- `PackageConfig.vue`
- 客户详情套餐绑定页面

任务:

- `package_plan` 支持 `audience_type/package_status/partner_points/partner_visible_config_json/internal_delivery_config_json`。
- 合伙人套餐只展示合伙人可见配置。
- `PUT /api/admin/partner-packages/{id}` 只允许编辑草稿且未绑定套餐。
- 已上架或已绑定套餐不允许原地修改核心配置。
- 客户首单消耗前可修改套餐;首单消耗后锁定快照。

验收:

- 合伙人只能看到上架的合伙人套餐。
- 合伙人看不到隐藏渠道配置。
- 已绑定套餐禁止删除,只能停用。
- 客户套餐锁定后,后续项目读取锁定快照。

### T08 启动申请提交和取消

依赖: T02, T03, T05, T06, T07

改造点:

- 新增 `ProjectStartRequestService`
- 新增 partner controller
- `ProjectService`
- `ProjectDistributionChannelAllocationService`
- `ProjectQuotaSnapshot` mapper/service

任务:

- 实现 `POST /api/partner/projects/{id}/start-requests`。
- 实现 `POST /api/partner/projects/{id}/start-requests/{requestId}/cancel`。
- 提交时校验客户已绑定合伙人套餐、项目可提交、实时剩余额度、首单积分充足。
- 提交时占用 `partner_allocated_quota`。
- 取消时释放占用额度,项目回到 `draft`。
- 捕获 `active_submitted_project_id` 唯一键冲突,转成“项目已有待审批申请”业务错误或幂等返回。
- 随任务交付启动申请并发提交测试和事务回滚测试。

验收:

- 合伙人交付员工不能提交启动申请。
- 合伙人负责人可提交自己合伙人的项目。
- 同项目并发提交不会产生两个 `submitted` 申请。
- 取消申请不取消项目本身。
- 取消后额度释放,项目可修改后重新提交。

### T09 总部审批和配额锁定

依赖: T08

改造点:

- admin project start request controller
- `ProjectStartRequestService`
- `ProjectQuotaSnapshot` service
- 合伙人积分流水服务
- `CompanyService.transferOwner` 调用链

任务:

- 实现审批通过接口。
- 实现驳回接口。
- 审批通过时再次校验积分。
- 若为客户首个审核通过并进入总部处理的项目,按 `套餐积分 * 折扣系数` 消耗积分。
- 审批通过时保存套餐快照、折扣快照、项目配额快照。
- 审批通过后项目进入 `approved_pending_setup`。
- 驳回释放占用额度,项目进入 `rejected`,允许修改重提。
- 客户内部负责人停用时阻断审批,要求先客户移交。
- 随任务交付审批事务一致性测试。

验收:

- 总部有审批权限的角色才能审批。
- 审批通过后合伙人不能撤销申请。
- 审批通过后配额快照锁定。
- 驳回后不扣积分,额度释放。
- 首单积分不足时审批失败且无半成功数据。

### T10 总部配置完成和正式启动

依赖: T09

改造点:

- `ProjectStateGuard`
- `ProjectService`
- 总部项目详情/准备清单相关接口

任务:

- 增加总部配置完成动作,将项目从 `approved_pending_setup` 流转到 `setup_ready`。
- 总部正式启动只允许 `setup_ready -> active`。
- 保持指纹浏览器、本地助手、自媒体账号绑定只对总部可见和可操作。

验收:

- 合伙人账号不能看到或调用配置完成/正式启动接口。
- 总部未配置完成前不能正式启动。
- 正式启动后合伙人不能修改已审批通过项目的关键资料。

### T11 诊断报告幂等和次数/积分流水

依赖: T02, T05, T06

改造点:

- `PresaleReport`
- `PresaleReportService`
- 新增 `PartnerPresaleReportQuotaTxn` entity/mapper/service
- 合伙人积分流水服务
- 诊断报告前端入口

任务:

- 合伙人创建诊断报告必须传 `requestId`。
- 规范化请求参数并计算 `request_hash`。
- 保存 `request_payload_snapshot_json`。
- 免费次数充足时预占次数;不足时预占积分。
- 成功后 `reserved -> confirmed`。
- 失败后 `reserved -> refunded`。
- 超时进入 `manual_review`,额度/积分继续冻结。
- 捕获 `partner_id + request_id` 唯一键冲突:
  - `request_hash` 一致时返回已有报告/任务状态。
  - `request_hash` 不一致时返回幂等冲突。
- 随任务交付诊断报告幂等、并发、失败回补测试。

验收:

- 并发请求不会重复扣次数或积分。
- 失败能回补次数或积分。
- 人工重跑不重复预占。
- 合伙人负责人可看本合伙人诊断报告。
- 合伙人交付员工不能看诊断报告。
- 总部管理员可看全部合伙人诊断报告。

### T12 客户内部负责人移交复用

依赖: T05, T09

改造点:

- `CompanyService.transferOwner`
- `POST /api/companies/{id}/owner-transfer`
- 可选 `POST /api/admin/companies/{id}/owner-transfer`
- 权限种子和断言测试

任务:

- 复用现有 `CompanyService.transferOwner`。
- 保持 `delivery.assignment.manage` 权限。
- 若新增 admin 包装接口,只能调用同一服务,不能复制逻辑。
- 活动日志记录原负责人、新负责人、操作人、原因。
- 启动审批时自动沿用有效客户负责人。

验收:

- 原负责人停用时审批阻断。
- 移交后审批可沿用新负责人。
- 管理者是否可操作取决于权限种子;若开放必须同步断言。

### T13 合伙人员工分配接口

依赖: T05

改造点:

- `CompanyService`
- partner company controller
- admin partner company controller
- 活动日志服务
- 客户详情页面

任务:

- 实现 `POST /api/partner/companies/{id}/staff-owner`。
- 实现 `POST /api/admin/partner-companies/{id}/staff-owner`。
- 允许负责人分配/取消分配本合伙人启用状态交付员工。
- 总部可兜底调整同一字段。
- 写活动日志。

验收:

- 新建客户默认不分配给交付员工。
- 分配后交付员工可见该客户及下游数据。
- 取消分配后交付员工不可见该客户及下游数据。
- 停用的合伙人员工不能被分配。

### T14 前端最小闭环

依赖: T06, T08, T09, T11, T13

改造点:

- 合伙人客户页
- 合伙人项目页
- 合伙人套餐/配额展示
- 合伙人诊断报告页
- 总部启动审批页
- 总部客户详情/分配入口

任务:

- 合伙人项目列表显示 `projectDisplayStatus`。
- 合伙人项目详情只展示 Partner DTO 字段。
- 合伙人负责人可提交/取消启动申请。
- 合伙人负责人可分配交付员工。
- 合伙人诊断报告显示剩余次数、扣除积分、生成状态。
- 总部审批页展示 Admin DTO、隐藏渠道快照、积分和配额快照。

验收:

- 合伙人页面看不到隐藏渠道、指纹浏览器、自媒体账号绑定、本地助手。
- 状态筛选与后端 `projectDisplayStatus` 一致。
- 启动申请、驳回、取消、审批通过后页面状态自动刷新。

### T15 自动化测试与回归

依赖: T01-T14

任务:

- 增加状态 resolver 单元测试。
- 增加项目启动申请并发测试。
- 增加 DTO 防泄漏测试。
- 增加合伙人员工数据范围测试。
- 增加诊断报告幂等/失败回补测试。
- 增加客户移交和员工分配审计测试。

验收:

- `active_submitted_project_id` 并发场景测试通过。
- `partner_id + request_id` 幂等场景测试通过。
- 合伙人接口响应字段快照测试不包含隐藏字段。
- 旧 `pending_start/expired` 项目兼容测试通过。

## 4. 推荐开发顺序

第一批:

- T00 迁移预检与发布检查
- T01 数据库迁移: 项目状态与字典
- T02 数据库迁移: 启动申请、配额快照、诊断流水
- T03 状态常量与 resolver

第二批:

- T04 `partner_viewer` 移除
- T05 合伙人员工数据范围
- T06 合伙人和总部 DTO 分层

第三批:

- T07 合伙人套餐和客户套餐绑定
- T08 启动申请提交和取消
- T09 总部审批和配额锁定
- T10 总部配置完成和正式启动

第四批:

- T11 诊断报告幂等和次数/积分流水
- T12 客户内部负责人移交复用
- T13 合伙人员工分配接口
- T14 前端最小闭环

收尾:

- T15 自动化测试与回归

## 5. 开工前确认项

- 确认当前生产 MySQL 版本支持生成列唯一索引。
- 确认生成列使用 `STORED` 还是 `VIRTUAL`;若版本或执行计划不稳定,改用辅助字段 + 事务行锁方案。
- 确认唯一键冲突异常统一映射为业务错误或幂等返回,不得将数据库异常直出前端。
- 确认合伙人套餐配置中 Agent 官网属于合伙人可见渠道,论坛/平台网站、行业资讯站、权威媒体属于总部隐藏交付快照。
- 确认阶段一不做复杂套餐迁移和首单撤销返还流程。
