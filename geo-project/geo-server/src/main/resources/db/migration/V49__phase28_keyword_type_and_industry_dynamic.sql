-- ============================================================
-- V49: keyword type/industry dynamic support
-- ============================================================

-- default type options
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT 'search', 'type', '搜索词', 10, 1
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word
    WHERE affix_kind = 'type' AND `type` = 'search'
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT 'qa', 'type', '问答词', 20, 1
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word
    WHERE affix_kind = 'type' AND `type` = 'qa'
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT 'brand', 'type', '品牌词', 30, 1
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word
    WHERE affix_kind = 'type' AND `type` = 'brand'
);

-- global industry words
INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT 'global', 'industry', '公司', 10, 1
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word
    WHERE affix_kind = 'industry' AND `type` = 'global' AND word_text = '公司'
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT 'global', 'industry', '机构', 20, 1
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word
    WHERE affix_kind = 'industry' AND `type` = 'global' AND word_text = '机构'
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT 'global', 'industry', '平台', 30, 1
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word
    WHERE affix_kind = 'industry' AND `type` = 'global' AND word_text = '平台'
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT 'global', 'industry', '服务商', 40, 1
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word
    WHERE affix_kind = 'industry' AND `type` = 'global' AND word_text = '服务商'
);

INSERT INTO keyword_affix_word (`type`, affix_kind, word_text, sort_order, enabled)
SELECT 'global', 'industry', '服务', 50, 1
WHERE NOT EXISTS (
    SELECT 1 FROM keyword_affix_word
    WHERE affix_kind = 'industry' AND `type` = 'global' AND word_text = '服务'
);
