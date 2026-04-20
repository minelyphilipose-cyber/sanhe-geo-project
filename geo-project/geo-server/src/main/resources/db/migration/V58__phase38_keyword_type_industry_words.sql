-- ============================================================
-- V58: import type-specific industry words for keyword groups
-- ============================================================

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '品牌' AS word_text, 10 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '厂家' AS word_text, 20 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '厂商' AS word_text, 30 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '企业' AS word_text, 40 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '公司' AS word_text, 50 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '集团' AS word_text, 60 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '商家' AS word_text, 70 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '供应商' AS word_text, 80 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '制造商' AS word_text, 90 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '生产商' AS word_text, 100 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '代理商' AS word_text, 110 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '经销商' AS word_text, 120 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '运营商' AS word_text, 130 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '开发商' AS word_text, 140 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '服务商' AS word_text, 150 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '连锁店' AS word_text, 160 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '旗舰店' AS word_text, 170 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '专卖店' AS word_text, 180 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '老字号' AS word_text, 190 AS sort_order UNION ALL
    SELECT 'brand' AS type_code, 'industry' AS affix_kind, '工作室' AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '门店' AS word_text, 10 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '网点' AS word_text, 20 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '营业厅' AS word_text, 30 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '体验店' AS word_text, 40 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '展厅' AS word_text, 50 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '服务中心' AS word_text, 60 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '办事处' AS word_text, 70 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '分公司' AS word_text, 80 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '分店' AS word_text, 90 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '直营店' AS word_text, 100 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '加盟店' AS word_text, 110 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '代理点' AS word_text, 120 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '维修点' AS word_text, 130 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '服务站' AS word_text, 140 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '实体店' AS word_text, 150 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '总部' AS word_text, 160 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '基地' AS word_text, 170 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '园区' AS word_text, 180 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '仓库' AS word_text, 190 AS sort_order UNION ALL
    SELECT 'location' AS type_code, 'industry' AS affix_kind, '配送中心' AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '行业' AS word_text, 10 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '产业' AS word_text, 20 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '领域' AS word_text, 30 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '赛道' AS word_text, 40 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '市场' AS word_text, 50 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '板块' AS word_text, 60 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '生态' AS word_text, 70 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '产业链' AS word_text, 80 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '供应链' AS word_text, 90 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '价值链' AS word_text, 100 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '细分市场' AS word_text, 110 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '垂直领域' AS word_text, 120 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '新兴产业' AS word_text, 130 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '传统行业' AS word_text, 140 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '朝阳行业' AS word_text, 150 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '蓝海市场' AS word_text, 160 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '红海市场' AS word_text, 170 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '上游' AS word_text, 180 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '中游' AS word_text, 190 AS sort_order UNION ALL
    SELECT 'industry' AS type_code, 'industry' AS affix_kind, '下游' AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '产品' AS word_text, 10 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '服务' AS word_text, 20 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '方案' AS word_text, 30 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '套餐' AS word_text, 40 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '课程' AS word_text, 50 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '系统' AS word_text, 60 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '软件' AS word_text, 70 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '工具' AS word_text, 80 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '平台' AS word_text, 90 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '解决方案' AS word_text, 100 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '项目' AS word_text, 110 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '计划' AS word_text, 120 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '模式' AS word_text, 130 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '版本' AS word_text, 140 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '配置' AS word_text, 150 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '机构' AS word_text, 160 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '顾问' AS word_text, 170 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '咨询' AS word_text, 180 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '外包' AS word_text, 190 AS sort_order UNION ALL
    SELECT 'decision' AS type_code, 'industry' AS affix_kind, '托管' AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '报价' AS word_text, 10 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '套餐' AS word_text, 20 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '服务' AS word_text, 30 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '商品' AS word_text, 40 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '产品' AS word_text, 50 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '课程' AS word_text, 60 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '会员' AS word_text, 70 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '订阅' AS word_text, 80 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '许可证' AS word_text, 90 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '授权' AS word_text, 100 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '定制' AS word_text, 110 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '租赁' AS word_text, 120 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '托管' AS word_text, 130 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '外包' AS word_text, 140 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '代运营' AS word_text, 150 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '年卡' AS word_text, 160 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '月卡' AS word_text, 170 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '体验装' AS word_text, 180 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '试用版' AS word_text, 190 AS sort_order UNION ALL
    SELECT 'transaction' AS type_code, 'industry' AS affix_kind, '企业版' AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '产品' AS word_text, 10 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '方案' AS word_text, 20 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '平台' AS word_text, 30 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '工具' AS word_text, 40 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '软件' AS word_text, 50 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '系统' AS word_text, 60 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '服务' AS word_text, 70 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '机构' AS word_text, 80 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '品牌' AS word_text, 90 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '供应商' AS word_text, 100 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '框架' AS word_text, 110 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '技术' AS word_text, 120 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '模式' AS word_text, 130 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '标准' AS word_text, 140 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '协议' AS word_text, 150 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '开源方案' AS word_text, 160 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '商业方案' AS word_text, 170 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, 'SaaS' AS word_text, 180 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '本地部署' AS word_text, 190 AS sort_order UNION ALL
    SELECT 'comparison' AS type_code, 'industry' AS affix_kind, '混合方案' AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '产品' AS word_text, 10 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '品牌' AS word_text, 20 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '平台' AS word_text, 30 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '厂商' AS word_text, 40 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '服务商' AS word_text, 50 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '软件' AS word_text, 60 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '工具' AS word_text, 70 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '系统' AS word_text, 80 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '方案' AS word_text, 90 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '供应商' AS word_text, 100 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '竞品' AS word_text, 110 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '替代品' AS word_text, 120 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '同类产品' AS word_text, 130 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '对标产品' AS word_text, 140 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '平替' AS word_text, 150 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '头部玩家' AS word_text, 160 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '新入局者' AS word_text, 170 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '挑战者' AS word_text, 180 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '领导者' AS word_text, 190 AS sort_order UNION ALL
    SELECT 'competitor' AS type_code, 'industry' AS affix_kind, '跟随者' AS word_text, 200 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT v.type_code, v.affix_kind, v.word_text, v.sort_order, 1
FROM (
    SELECT 'qa' AS type_code, 'industry' AS affix_kind, '公司' AS word_text, 10 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'industry' AS affix_kind, '机构' AS word_text, 20 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'industry' AS affix_kind, '平台' AS word_text, 30 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'industry' AS affix_kind, '服务商' AS word_text, 40 AS sort_order UNION ALL
    SELECT 'qa' AS type_code, 'industry' AS affix_kind, '服务' AS word_text, 50 AS sort_order
) v
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word k
    WHERE k.`type` = v.type_code
      AND k.affix_kind = v.affix_kind
      AND k.word_text = v.word_text
);
