-- ============================================================
-- V11: unified dictionary items
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type   VARCHAR(64) NOT NULL,
    dict_key    VARCHAR(64) NOT NULL,
    dict_value  VARCHAR(128) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 100,
    enabled     TINYINT(1) NOT NULL DEFAULT 1,
    remark      VARCHAR(255) NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dict_type_key (dict_type, dict_key),
    KEY idx_dict_type_enabled_sort (dict_type, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system dictionary items';

-- role
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'role', 'super_admin', '超级管理员', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'role' AND dict_key = 'super_admin');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'role', 'manager', '管理者', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'role' AND dict_key = 'manager');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'role', 'delivery_manager', '交付负责人', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'role' AND dict_key = 'delivery_manager');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'role', 'operator', '运营', 40 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'role' AND dict_key = 'operator');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'role', 'sales', '销售', 50 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'role' AND dict_key = 'sales');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'role', 'partner', '合伙人主账号', 60 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'role' AND dict_key = 'partner');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'role', 'partner_staff', '合伙人员工', 70 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'role' AND dict_key = 'partner_staff');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'role', 'partner_viewer', '合伙人只读', 80 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'role' AND dict_key = 'partner_viewer');

-- owner/company/brand/project
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'owner_type', 'direct', '直营', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'owner_type' AND dict_key = 'direct');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'owner_type', 'partner', '合伙人', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'owner_type' AND dict_key = 'partner');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'owner_type', 'joint', '联合', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'owner_type' AND dict_key = 'joint');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'company_status', 'potential', '潜在', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'company_status' AND dict_key = 'potential');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'company_status', 'signed', '已签约', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'company_status' AND dict_key = 'signed');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'company_status', 'inactive', '停用', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'company_status' AND dict_key = 'inactive');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'brand_status', 'draft', '草稿', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'brand_status' AND dict_key = 'draft');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'brand_status', 'active', '启用', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'brand_status' AND dict_key = 'active');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'brand_status', 'archived', '归档', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'brand_status' AND dict_key = 'archived');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_status', 'draft', '草稿', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_status' AND dict_key = 'draft');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_status', 'active', '进行中', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_status' AND dict_key = 'active');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_status', 'paused', '暂停', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_status' AND dict_key = 'paused');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_status', 'dispute', '争议中', 40 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_status' AND dict_key = 'dispute');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_status', 'completed', '已完成', 50 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_status' AND dict_key = 'completed');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_status', 'archived', '已归档', 60 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_status' AND dict_key = 'archived');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'pending_start', '待启动', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'pending_start');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'collecting_materials', '资料收集中', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'collecting_materials');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'baseline_diagnosis', '基线诊断中', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'baseline_diagnosis');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'building_questions', '问题池构建', 40 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'building_questions');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'executing', '执行中', 50 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'executing');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'biweekly_feedback', '双周反馈', 60 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'biweekly_feedback');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'monthly_report', '月报阶段', 70 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'monthly_report');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'quarterly_report', '季报阶段', 80 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'quarterly_report');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'needs_renewal', '待续费', 90 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'needs_renewal');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'high_risk', '高风险', 100 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'high_risk');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'dispute_handling', '争议处理中', 110 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'dispute_handling');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'project_stage', 'completed', '已完结', 120 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_stage' AND dict_key = 'completed');

-- package & partner
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'package_type', 'trial_6980', '试点版(6980/3个月)', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'package_type' AND dict_key = 'trial_6980');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'package_type', 'standard_12800', '标准版(12800/12个月)', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'package_type' AND dict_key = 'standard_12800');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'package_type', 'growth_26800', '增长版(26800/12个月)', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'package_type' AND dict_key = 'growth_26800');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_level', 'level_29800', '29800档(3折)', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_level' AND dict_key = 'level_29800');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_level', 'level_59800', '59800档(2.5折)', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_level' AND dict_key = 'level_59800');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_level', 'level_99800', '99800档(2折)', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_level' AND dict_key = 'level_99800');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_status', 'active', '启用', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_status' AND dict_key = 'active');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_status', 'paused', '暂停', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_status' AND dict_key = 'paused');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_status', 'closed', '关闭', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_status' AND dict_key = 'closed');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'training_status', 'not_trained', '未培训', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'training_status' AND dict_key = 'not_trained');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'training_status', 'in_training', '培训中', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'training_status' AND dict_key = 'in_training');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'training_status', 'passed', '已通过', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'training_status' AND dict_key = 'passed');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'training_status', 'production_enabled', '可正式交付', 40 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'training_status' AND dict_key = 'production_enabled');

-- finance txn
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_txn_type', 'recharge', '充值', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_txn_type' AND dict_key = 'recharge');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_txn_type', 'deduction', '扣款', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_txn_type' AND dict_key = 'deduction');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_txn_type', 'manual_adjust', '手工调整', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_txn_type' AND dict_key = 'manual_adjust');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_biz_type', 'partner_prepaid', '预存充值', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_biz_type' AND dict_key = 'partner_prepaid');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_biz_type', 'project_signing', '项目签约扣款', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_biz_type' AND dict_key = 'project_signing');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'partner_biz_type', 'finance_adjust', '财务调整', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'partner_biz_type' AND dict_key = 'finance_adjust');

-- report
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_type', 'presale_diagnosis', '售前诊断报告', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_type' AND dict_key = 'presale_diagnosis');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_type', 'biweekly', '双周简报', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_type' AND dict_key = 'biweekly');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_type', 'monthly', '月度报表', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_type' AND dict_key = 'monthly');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_type', 'quarterly', '季度报表', 40 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_type' AND dict_key = 'quarterly');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_type', 'management', '管理层汇总', 50 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_type' AND dict_key = 'management');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_status', 'generating', '生成中', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_status' AND dict_key = 'generating');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_status', 'pending_review', '待复核', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_status' AND dict_key = 'pending_review');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_status', 'auto_approved', '自动通过', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_status' AND dict_key = 'auto_approved');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_status', 'manually_approved', '人工通过', 40 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_status' AND dict_key = 'manually_approved');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_status', 'intercepted', '已拦截', 50 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_status' AND dict_key = 'intercepted');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_status', 'published', '已发布', 60 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_status' AND dict_key = 'published');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'report_status', 'archived', '已归档', 70 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_status' AND dict_key = 'archived');

-- platform / alert / question
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'platform_priority', 'P0', 'P0(核心)', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'platform_priority' AND dict_key = 'P0');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'platform_priority', 'P1', 'P1(重要)', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'platform_priority' AND dict_key = 'P1');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'platform_priority', 'P2', 'P2(补充)', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'platform_priority' AND dict_key = 'P2');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'platform_health', 'normal', '正常', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'platform_health' AND dict_key = 'normal');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'platform_health', 'slow_response', '响应慢', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'platform_health' AND dict_key = 'slow_response');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'platform_health', 'high_failure', '高失败率', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'platform_health' AND dict_key = 'high_failure');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'platform_health', 'degraded', '降级', 40 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'platform_health' AND dict_key = 'degraded');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'platform_health', 'manual_takeover', '人工接管', 50 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'platform_health' AND dict_key = 'manual_takeover');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'platform_health', 'maintenance', '维护中', 60 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'platform_health' AND dict_key = 'maintenance');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'alert_severity', 'info', '信息', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'alert_severity' AND dict_key = 'info');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'alert_severity', 'warning', '警告', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'alert_severity' AND dict_key = 'warning');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'alert_severity', 'critical', '严重', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'alert_severity' AND dict_key = 'critical');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_type', 'brand', '品牌类', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_type' AND dict_key = 'brand');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_type', 'location', '地域类', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_type' AND dict_key = 'location');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_type', 'industry', '行业类', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_type' AND dict_key = 'industry');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_type', 'decision', '决策类', 40 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_type' AND dict_key = 'decision');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_type', 'transaction', '交易类', 50 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_type' AND dict_key = 'transaction');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_type', 'qa', '问答类', 60 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_type' AND dict_key = 'qa');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_type', 'comparison', '对比类', 70 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_type' AND dict_key = 'comparison');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_type', 'competitor', '竞品类', 80 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_type' AND dict_key = 'competitor');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_priority', 'A', 'A类', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_priority' AND dict_key = 'A');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_priority', 'B', 'B类', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_priority' AND dict_key = 'B');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'question_priority', 'C', 'C类', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'question_priority' AND dict_key = 'C');

-- activity action
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'company.create', '创建客户', 10 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'company.create');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'company.update', '更新客户', 20 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'company.update');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'company.delete', '删除客户', 30 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'company.delete');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'brand.create', '创建品牌', 40 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'brand.create');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'brand.update', '更新品牌', 50 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'brand.update');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'brand.delete', '删除品牌', 60 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'brand.delete');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'project.create', '创建项目', 70 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'project.create');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'project.update', '更新项目', 80 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'project.update');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'project.delete', '删除项目', 90 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'project.delete');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'project.sign_and_deduct', '签约并扣款', 100 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'project.sign_and_deduct');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'project.status.update', '更新项目状态', 110 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'project.status.update');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'project.stage.update', '更新项目阶段', 120 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'project.stage.update');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order) SELECT 'activity_action', 'project.flow.update', '更新项目流转', 130 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'project.flow.update');
