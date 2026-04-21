# P1·C 交付:Prompt 库导入 SQL(通用首批)

Flyway 迁移脚本 V63,向 `presale_prompt_template` 插入 30 条通用 prompt 模板。

## 文件

```
src/main/resources/db/migration/
└── V63__seed_presale_prompt_templates_universal.sql    30 条通用 prompt
```

## 决策路径(用户确认)

| # | 决策项 | 选择 |
|---|---|---|
| 1 | 覆盖范围 | **A** 仅通用 30 条(分行业 390 条留给后续增量脚本) |
| 2 | 身份展开 | **1c** 全部 `industry='_ALL_' / industry_role='_ALL_'`,不做细分 |
| 3 | 竞品变量 | **A1** 原 `{competitor_a/b/c}` 三变量合并改写为 v1.2 单变量 `{competitor}`(Top3 拼接文本一次注入) |
| 4 | 第二轮凑足 5 条 | **X1** 原通用库仅 3 条含竞品(CMP_GEN_001/002/003),改写 CMP_GEN_004/005 加入 `{competitor}` 凑足 5 条 |
| 5 | Flyway 版本 | **V63** 独立文件,不污染 V62 建表脚本 |

## 导入结果(预期)

| 指标 | 数值 |
|---|---|
| 总 INSERT 条数 | 30 |
| 第一轮(has_competitor_var=0) | 25 |
| 第二轮(has_competitor_var=1) | 5 |
| 推荐型 REC_GEN | 10 |
| 对比型 CMP_GEN | 5(全含 `{competitor}`) |
| 问题型 PRB_GEN | 5 |
| 认知型 CGN_GEN | 5 |
| 场景型 SCN_GEN | 5 |
| 商业价值 `高` | 15(REC + CMP) |
| 商业价值 `中` | 15(PRB + CGN + SCN) |

**对齐 v1.2 契约的 660 调用口径:**
- 第一轮:25 prompt × 11 platform × 2(测试+分析) = 550
- 第二轮:5 prompt × 11 platform × 2(测试+分析) = 110
- 合计:**660 次 LLM 调用** ✓

## 变量占位符

SQL 里没有对变量的强校验(`prompt_content TEXT`),运营可以在 UI 上自由增减。但渲染层必须认识这几个变量:

| 变量 | 来源 | 示例 |
|---|---|---|
| `{brand}` | `presale_report.brand_name` | 海底捞 |
| `{industry}` | `presale_report.industry` 的字典 value | 餐饮 |
| `{industry_role}` | `presale_report.industry_role` 字典 value | 连锁品牌 |
| `{region}` | `presale_report.region` | 北京市 |
| `{product}` | 运营在补全环节填入 | 火锅 |
| `{competitor}` | **仅第二轮**:CompetitorDetector 产出 Top3 后一次拼接注入,如 `"巴奴毛肚火锅、呷哺呷哺、小龙坎"` |

**渲染顺序:** v1.2 契约约定 `{competitor}` 是**一次性注入**(不展开成 3 次调用),前端/后端 PromptRenderer 在替换时应对同一条 prompt 的所有 `{competitor}` 占位符用同一份 Top3 拼接文本替换。

## X1 改写清单(业务侧 review 时重点看这 2 条)

| prompt_code | 原 v1.0 prompt | 改写后 | 改写理由 |
|---|---|---|---|
| CMP_GEN_004 | `{industry}行业的Top品牌对比分析` | `{industry}行业,{competitor} 这几个 Top 品牌的对比分析` | 原文不含竞品变量,加入 `{competitor}` 使其能进第二轮 |
| CMP_GEN_005 | `{brand}在{industry}行业处于什么水平?` | `{brand}和 {competitor} 相比,在{industry}行业各自处于什么水平?` | 保留"水平对标"语义,加入竞品参照物提升信号量 |

如果业务侧觉得改写后语感不够自然,**可以直接 UPDATE `prompt_content`**,不改 `prompt_code`,SQL remark 字段有完整溯源标记。

## 后续增量脚本规划(非本次 P1·C 范围)

原 Prompt 库 v1.0 还有 390 条分行业 prompt(12 行业 × 平均 32 条)未导入。后续增量脚本命名建议:

```
V64__seed_presale_prompt_templates_restaurant.sql    餐饮 ~35 条
V65__seed_presale_prompt_templates_education.sql     教培 ~35 条
V66__seed_presale_prompt_templates_medical_beauty.sql 医美 ~35 条
...
```

**分行业导入前必须解决的 TODO**(V63 SQL 内已注明):

1. **⚠️ prompt_code 唯一性编码规则**(**Codex 提出,V64+ 开工前必须先定**) —— V62 `UNIQUE KEY uk_prompt_code` 硬约束同一 code 只能出现一次,不能通过 `(code, industry, industry_role)` 组合唯一。若 V64+ 要把一条"原始 prompt"展开成多行业/多身份多行(原 Prompt 库 v1.0 里 `applicable_industries: ["A", "B"]` 这种情况),**必须重新编码**,不能复用 `REC_GEN_001` 这种原 code。

   候选编码规则(V64+ 开工前三选一):

   - **方案 A(推荐)** — 行业/身份**后缀化**,保留原 code 作为"模板族":
     ```
     REC_GEN_001                     真通用 (_ALL_/_ALL_),V63 本批是这种
     REC_GEN_001__REST               餐饮通用 (restaurant/_ALL_)
     REC_GEN_001__REST__CHN          餐饮 × 连锁品牌 (restaurant/chain_brand)
     REC_GEN_001__EDU__ORG           教培 × 机构
     ```
     优点:原 v1.0 编号可溯源;缺点:code 变长(`VARCHAR(50)` 够用不宽裕)

   - **方案 B** — 行业**前缀化**,完全重编码:
     ```
     REST_REC_001 / REST_CHN_REC_001
     ```
     优点:按前缀排序天然分组;缺点:与 v1.0 文档编号脱钩,溯源靠 remark

   - **方案 C** — 改 V62 表结构,唯一键改为 `(prompt_code, industry, industry_role)`:
     ```sql
     ALTER TABLE presale_prompt_template
         DROP INDEX uk_prompt_code,
         ADD UNIQUE KEY uk_code_industry_role (prompt_code, industry, industry_role);
     ```
     优点:无需改 code,展开零心智负担;缺点:改已落库表结构,回滚窗口紧

   **V63 本批 30 条全部是 `_ALL_/_ALL_` 真通用形态,不与以上任何方案冲突**,按 A/B/C 任一方案推进 V64 都不需要回改 V63。

2. **身份字典映射** —— 原 Prompt 库里"门店"/"工厂"/"电商品牌"/"SaaS" 到 V62 `presale_industry_role` 字典 key 的映射:
   - "门店" → `single_store`?
   - "工厂" → `manufacturer`?
   - "电商品牌" → `chain_brand`?
   - "SaaS" → `service_provider`?或新增字典项?
3. **行业字典映射** —— 原 Prompt 库的"餐饮"/"教培"等中文名到 V62 `presale_industry` 字典 key 的映射(大部分可直接查 V62 v4 的 `sys_dict_item` INSERT 部分)
4. **multi-industry prompt 展开** —— 原库中 `applicable_industries: ["A", "B"]` 的多行业 prompt,在 V62 单值字段下需要展开成多条 INSERT(每组合一条),且新 code 按 TODO 1 的编码规则生成
5. **分行业 CMP prompt 的 X1 改写** —— 若分行业 CMP 条数不足 5,也需按 X1 策略补齐

## 执行方式

前置条件:V62 v4 已执行(建表 + 字典初始化完成)。

Flyway 自动按文件名版本号检出:
```
V61 (existing)
  → V62 (建表 + 字典 + 权限)
    → V63 (本次:通用 prompt 30 条)  ← HEAD
```

回滚方式(仅 DML,不改表结构):
```sql
DELETE FROM presale_prompt_template
WHERE prompt_code LIKE '___GEN_%'      -- REC_GEN / CMP_GEN / PRB_GEN / CGN_GEN / SCN_GEN
  AND industry = '_ALL_'
  AND industry_role = '_ALL_';
DELETE FROM flyway_schema_history WHERE version = '63';
```

## 验证 SQL(UAT 用)

```sql
-- 总数
SELECT COUNT(*) FROM presale_prompt_template;  -- 30

-- 两轮分布
SELECT
  CASE has_competitor_var WHEN 0 THEN '第一轮' ELSE '第二轮' END AS round_name,
  COUNT(*) AS cnt
FROM presale_prompt_template
GROUP BY has_competitor_var;
-- 第一轮 25 / 第二轮 5

-- 意图分布
SELECT category, COUNT(*) FROM presale_prompt_template GROUP BY category;
-- 推荐型 10 / 对比型 5 / 问题型 5 / 认知型 5 / 场景型 5

-- 商业价值分布
SELECT business_value, COUNT(*) FROM presale_prompt_template GROUP BY business_value;
-- 高 15 / 中 15

-- 第二轮 5 条完整检查(全部含 {competitor} 占位符,不含 {competitor_a/b/c})
SELECT prompt_code, prompt_content FROM presale_prompt_template
WHERE has_competitor_var = 1;
-- 期望:5 条,prompt_content 均含 "{competitor}",不含 "{competitor_a}"/"{competitor_b}"/"{competitor_c}"

-- 排序检查(sort_order 应按 101-110, 201-205, 301-305, ...)
SELECT prompt_code, sort_order FROM presale_prompt_template ORDER BY sort_order;
```

## 依赖

- MySQL 8.0+(V62 v4 已要求)
- Flyway(仓库现有)
- V62 v4 已落库(含字典 `presale_industry` / `presale_industry_role` 初始化)

## 后续 P1 产出

- **P1·D 优化规则库 v1.0** — 8-10 条规则,`rule_code` 映射到本批 prompt 的 `category`(如 `RULE_COVERAGE_LOW_RECOMMEND` 统计"推荐型"覆盖率)
- **分行业 prompt 增量** — V64~V75 每行业一个脚本,需先解决上述 4 项 TODO
