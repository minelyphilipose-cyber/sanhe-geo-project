-- V78 · DROP 旧的 presale_platform_config 表(D59)
-- 前置条件: V77_1 / V77_2 已成功执行,所有 presale 调用方已切到 ai_platform_config
-- 执行时机: CP3 Gamma 验收通过后
-- 开发测试阶段无需观察期

DROP TABLE IF EXISTS presale_platform_config;
