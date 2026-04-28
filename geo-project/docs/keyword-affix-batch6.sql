-- 2026-04-28 批 6:function 功能词类型综合审计
-- 执行前已预检: function.prefix 无"企业版",不会触发唯一键冲突。

START TRANSACTION;

UPDATE keyword_affix_word
SET affix_kind = 'prefix',
    sub_category = '产品分级类',
    industry_tag = 'common',
    visual_tag = NULL,
    sort_order = sort_order + 1000
WHERE id = 212 AND type = 'function' AND affix_kind = 'industry';

COMMIT;
