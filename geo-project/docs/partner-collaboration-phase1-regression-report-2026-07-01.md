# 合伙人协作阶段一回归记录

日期: 2026-07-01

## 1. 回归结论

阶段一关键自动化回归通过,可以进入测试库环境验证与业务验收。

本次回归覆盖:

- 项目状态机、旧状态兼容、`projectDisplayStatus` 派生。
- 启动申请提交、撤销、审批、驳回、配置完成及并发互斥。
- 合伙人负责人、合伙人交付员工、总部交付负责人的权限与数据范围。
- 合伙人 DTO 防泄漏,包括隐藏渠道、B/C 问题口径、内部交付快照。
- 合伙人套餐草稿/上下架/锁定、客户套餐绑定快照。
- 首单积分消耗、审批流水审计字段、诊断报告额度/积分预占与失败回补。
- 客户移交、合伙人员工分配、员工唯一约束和停用联动。
- 前端合伙人入口、总部客户详情、启动审批相关页面的类型与构建闭合。

## 2. 已执行验证

### 2.1 后端关键测试集合

命令一:

```bash
mvn -q "-Dtest=ProjectFlowPolicyTest,ProjectDisplayStatusResolverTest,ProjectStateGuardTest,ProjectStartRequestServiceTest,InternalScopeServiceTest,PartnerResponseSanitizerTest,PackagePlanServiceTest,CompanyPackageBindingServiceTest,PartnerPresaleReportQuotaServiceTest,PresaleAccessServiceTest,CompanyServiceOwnerTransferTest,PartnerServiceTest,UserAdminServiceTest,WorkbenchServiceTest,KeywordGroupServiceDeleteTest,KeywordLlmQuestionServiceTest" test
```

命令二:

```bash
mvn -q "-Dtest=PresaleReportServiceTest,PresaleGenerateQuestionConcurrencyTest,ProjectDistributionChannelAllocationServiceTest" test
```

结果: 均通过。

覆盖重点:

- `draft/submitted/rejected/approved_pending_setup/setup_ready/active/paused/completed/archived/expired/pending_start` 状态规则。
- 通用状态接口禁止绕过申请审批流程态。
- 合伙人项目 `setup_ready -> active` 首次启动和 `paused -> active` 恢复。
- 旧 `pending_start/expired` 展示状态兼容。
- `partner_staff` 只能访问分配给自己的客户/项目/品牌。
- 合伙人接口响应净化与核心问题口径。
- 合伙人套餐非草稿不可原地修改核心配置。
- 启动申请条件更新、积分流水冲突回滚、配额快照锁定/释放。
- 诊断报告 `partner_id + request_id` 幂等、失败退款与 `manual_review` 标记。
- 诊断报告创建服务、问题生成并发与项目分发渠道配额服务的既有回归。
- 客户负责人移交、合伙人员工分配和停用联动。

### 2.2 后端编译

命令:

```bash
mvn -q -DskipTests compile
```

结果: 通过。

### 2.3 前端构建

命令:

```bash
npm run build
```

结果: 通过。

说明:

- 构建中仍有既有 `postcss-px-to-viewport` 插件弃用提示。
- 构建中仍有既有 chunk 体积告警。
- 未发现阶段一改造引入的 TypeScript 或构建错误。

## 3. 尚需环境验证

以下内容依赖真实测试 MySQL 与 Flyway 执行环境,本地单元测试不能完全替代:

- 执行 `geo-server/src/main/resources/db/precheck/partner_phase1_precheck.sql`。
- 按实际版本顺序执行阶段一迁移,至少覆盖:
  - `V275__partner_phase1_project_status_foundation.sql`
  - `V276__partner_phase1_request_quota_presale_schema.sql`
  - `V278__partner_phase1_remove_partner_viewer_entry.sql`
  - `V281__partner_phase1_start_request_txn_audit_fields.sql`
  - `V282__partner_presale_report_quota_config.sql`
  - `V283__partner_staff_unique_partner_constraint.sql`
- 验证 MySQL 生成列唯一索引:
  - `project_start_request.active_submitted_project_id`
  - `sys_user.active_partner_staff_partner_id`
- 验证项目状态触发器重建后不影响现有 `expired` 写入任务。
- 验证 `partner_viewer` 存量阻断脚本在测试库表现符合预期。

## 4. 阶段一收口判断

从自动化测试和编译构建角度,阶段一研发主链路已闭合。进入业务验收前,需要先完成测试库预检和 Flyway migration 实测,再按合伙人负责人、合伙人员工、总部交付负责人三类账号做一轮端到端手工验收。
