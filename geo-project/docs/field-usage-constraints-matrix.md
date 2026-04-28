# keyword_affix_word 字段使用一致性矩阵

> 用途:本文档作为 `keyword_affix_word` 表所有字段在不同 (type, affix_kind) 场景下的强约束清单,
> 服务于阶段一 / 阶段二代码维护。当字段含义不清或新人写代码时,以本文档为准。
>
> 编制日期:2026-04-28
> 编制依据:V90 迁移 SQL + KeywordAffixWordService 当前实现 + 阶段一词库审计成果(批 0-7)

## 0. 表基本结构

```sql
-- 字段顺序按 V90 之后的逻辑顺序整理(便于阅读),实际表中位置以 INFORMATION_SCHEMA 为准
CREATE TABLE keyword_affix_word (
    -- 主键
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- 词库分类(三元组唯一索引)
    type             VARCHAR(32)  NOT NULL,
    affix_kind       VARCHAR(16)  NOT NULL,
    word_text        VARCHAR(255) NOT NULL,

    -- V90 新增分类字段
    sub_category     VARCHAR(50)  NULL,
    visual_tag       VARCHAR(20)  NULL,
    industry_tag     VARCHAR(30)  NULL,

    -- 词库属性
    sort_order       INT          NOT NULL DEFAULT 100,
    enabled          TINYINT(1)   NOT NULL DEFAULT 1,
    is_manual        TINYINT(1)   NOT NULL DEFAULT 0,
    is_temporary     TINYINT(1)   NOT NULL DEFAULT 0,

    -- 临时词作用域
    scope_type       VARCHAR(16)  NULL,
    scope_id         BIGINT       NULL,

    -- 使用与添加追踪
    last_used_at     DATETIME     NULL,
    added_by_user_id BIGINT       NULL,

    -- 审批工作流字段
    approval_status  VARCHAR(20)  NOT NULL DEFAULT 'approved',
    approval_reason  TEXT         NULL,
    approved_by      BIGINT       NULL,
    approved_at      DATETIME     NULL,

    -- 标准时间戳
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_type_kind_word (type, affix_kind, word_text)
) ENGINE=InnoDB;
```

唯一键:`(type, affix_kind, word_text)`——同名词在不同 type 下允许共存(例如"进口"同时在 brand.prefix / function.prefix / qa.prefix)。

---

## 1. 字段必填/必空总表

| 字段 | 必填条件 | 必空条件 | 默认值 | 备注 |
|---|---|---|---|---|
| `id` | 永远必填 | — | auto_increment | 主键 |
| `type` | 永远必填 | — | — | 见 §2 取值表 |
| `affix_kind` | 永远必填 | — | — | 见 §2 取值表 |
| `word_text` | 永远必填 | — | — | 非空字符串,trim 后非空 |
| `sort_order` | 永远必填 | — | 100 | 同 (type, affix_kind) 内排序 |
| `enabled` | 永远必填 | — | 1 | 软删除标志 |
| `is_manual` | 永远必填 | — | 0 | 0=种子词,1=管理员/销售手动添加 |
| `is_temporary` | 永远必填 | — | 0 | 0=永久,1=临时 |
| `approval_status` | 永远必填 | — | `approved` | 见 §3 审批状态约束 |
| `sub_category` | 6 个新类型 enabled=1 词必填 | 历史类型 / enabled=0 可空 | NULL | 见 §4 |
| `visual_tag` | 仅 `decision.industry` / `function.industry` 必填 | 其他 (type, affix_kind) 必空 | NULL | 见 §5 |
| `industry_tag` | 仅 `function.prefix` 必填 | 其他 (type, affix_kind) 必空 | NULL | 见 §6 |
| `scope_type` | `is_temporary=1` 时必填 | `is_temporary=0` 时通常 NULL 或 `global` | NULL | 见 §7 |
| `scope_id` | `scope_type IN ('company','project')` 时必填 | `scope_type IS NULL` 或 `'global'` 时必空 | NULL | 见 §7 |
| `last_used_at` | — | 创建时为 NULL | NULL | 词被保存到任意组时更新(阶段二实现) |
| `added_by_user_id` | `is_manual=1` 时建议必填 | `is_manual=0` 时必空(种子词无人添加) | NULL | 阶段二维护页改造时补 |
| `approval_reason` | `approval_status='rejected'` 时建议必填 | `approval_status='approved'` 且 `is_manual=0` 时空 | NULL | 阶段二审批流时补 |
| `approved_by` | `approval_status='approved'` 且 `is_manual=1` 时建议必填 | `is_manual=0` 时空 | NULL | 阶段二审批流时补 |
| `approved_at` | 同 `approved_by` | 同 `approved_by` | NULL | 阶段二审批流时补 |
| `created_at` | 永远必填 | — | CURRENT_TIMESTAMP | DB 自动维护 |
| `updated_at` | 永远必填 | — | CURRENT_TIMESTAMP | DB 自动维护(ON UPDATE) |

---

## 2. type / affix_kind 取值约束

### type 取值

| 取值 | 类别 | 备注 |
|---|---|---|
| `brand` | 6 个新类型 | 品牌词,PRD 3.1 |
| `decision` | 6 个新类型 | 决策词,PRD 3.2 |
| `transaction` | 6 个新类型 | 成交词,PRD 3.3 |
| `comparison` | 6 个新类型 | 对比词,PRD 3.4 |
| `qa` | 6 个新类型 | 问答词,PRD 3.5 |
| `function` | 6 个新类型 | 功能词,PRD 3.6 |
| `location` | 历史类型 | V90 标记为历史,新建场景类型选择器不展示 |
| `industry` | 历史类型 | 同上 |
| `competitor` | 历史类型 | 同上 |
| `search` | 历史类型 | 老组兼容用,V90 已批量改写为 decision |

新建词的 type 必须在 6 个新类型之列。历史类型词只在迁移期保留,**不允许从代码层面新增历史类型词**。

### affix_kind 取值

| 取值 | 含义 | 适用 type |
|---|---|---|
| `area` | 地区词列 | 所有类型(由 KeywordTypeConfigVO.columns.area 控制是否渲染) |
| `prefix` | 前缀词列 | 普通类型(brand/decision/transaction/qa/function) |
| `core` | 核心词列(单核心) | **不进 keyword_affix_word 表**——核心词由用户每次输入,不进种子词库 |
| `industry` | 行业词列 | 普通类型(brand/decision/transaction/function 显示;qa.industry 历史下架) |
| `suffix` | 后缀词列 | 所有类型 |
| `compare` | 对比连接词列 | 仅 comparison 类型 |

**注**:keyword_affix_word 表实际不存 `core` 类型的行(核心词不进种子词库),所以 `affix_kind` 取值实际只用 area/prefix/industry/suffix/compare 五种。代码 `EDITABLE_AFFIX_KIND_SET` 也是这五个。

---

## 3. approval_status 状态约束

| 状态 | 业务含义 | is_manual 关系 |
|---|---|---|
| `approved` | 已审批通过(可被 options 接口返回) | 种子词(is_manual=0)永远是 approved;手动词(is_manual=1)审批通过后变为 approved |
| `pending` | 等待审批(options 不返回,只在审批列表展示) | 仅 is_manual=1 出现 |
| `rejected` | 审批拒绝(options 不返回) | 仅 is_manual=1 出现 |

阶段一时:
- 种子词全部 `is_manual=0, approval_status='approved'`
- 维护页临时词 INSERT 时 `is_manual=1, approval_status='approved'`(阶段一暂不实施完整审批流,直接通过)

阶段二补审批流时:
- 维护页 INSERT 时 `is_manual=1, approval_status='pending'`
- 审批通过 → `approved` + 写 `approved_by / approved_at`
- 审批拒绝 → `rejected` + 写 `approval_reason`

---

## 4. sub_category 取值约束

`sub_category` 是 V90 引入的子分类字段,**主要服务于前端 UI 按 subCategory 分段展示词列表**(阶段一对齐纪要议题 5.3)。

约束:
- **6 个新类型 enabled=1 词必填**——经过批 0-7 审计后,所有 enabled 词都补全了 sub_category
- **历史类型(location/industry/competitor)的 sub_category 全是 NULL**——这是有意保留,因为历史类型不在新 UI 渲染,无需分组
- **enabled=0 的词** sub_category 取值不强制,可空可有值(批 1 下架的"十大""厂商"保留了原 sub_category 是允许的)

### 已用的 sub_category 取值清单(按 type 分组)

| type | affix_kind | 已用 sub_category |
|---|---|---|
| brand | prefix | 正向评价类 / 品质特征类 / 来源类 / 规模类 / 资质类 |
| brand | industry | 主体词 / 品质特征 |
| brand | suffix | 了解类 / 评价类 / 归属类 |
| decision | prefix | 正向评价类 / 品牌品质类 / 资质类 / 场景类 |
| decision | industry | 行业词 |
| decision | suffix | 决策选择类 / 指南类 / 注意事项类 / 决策依据类 / 建议类 / 榜单类 / 推荐类 / 测评类 / 对比类 |
| transaction | prefix | 价格敏感类 / 性价比类 / 价格敏感类 / 优惠活动类 / 渠道类 / 渠道保障类 |
| transaction | industry | 通用主体 / 商业模型类 |
| transaction | suffix | 价格查询类 / 预算对比类 / 优惠类 / 购买渠道类 |
| comparison | suffix | 对比选择类 / 对比类 / 维度对比类 / 优劣分析类 / 替代类 |
| comparison | compare | 对比连接词 |
| qa | prefix | 来源类 / 新旧类 / 品质类 |
| qa | suffix | 使用类 / 维护类 / 故障类 / 综合类 / 攻略类 / 注意事项类 |
| function | prefix | 通用性能类 / 门窗行业 / 家电行业 / 建材行业 / 快消行业 / 工业品行业 / 服装行业 / 新旧/来源类 / 产品分级类 / 规模类 |
| function | industry | 功能行业词 |
| function | suffix | 功能后缀 |

新增维护页词时,sub_category 应从已存在的取值中选取,避免引入语义冲突的新分类。如确需新分类,需经词库审计签字。

---

## 5. visual_tag 取值约束

`visual_tag` 服务于前端按"toB / toC / common"为词项打小标签,辅助 AI 训练师/销售判断词的适用客户类型。

**严格约束**:`visual_tag` 仅在以下两个 (type, affix_kind) 必填,**其他场景应为 NULL**:

| 场景 | 必填 | 取值 |
|---|---|---|
| `decision.industry` | ✅ | `toB` / `toC` / `common` |
| `function.industry` | ✅ | `toB` / `toC` / `common`(实际批 0 INSERT 时统一用 `common`) |
| 其他 (type, affix_kind) | ❌ 必空 | NULL |

PRD 3.2.6 + 3.6.6 + 阶段一审计批 1/2/6 的设计依据。

阶段一审计成果:
- decision.industry 的 28 条 enabled 词全部带 visual_tag(common 5 / toC 1 / toB 22)
- function.industry 的 7 条 enabled 词全部 visual_tag=common

---

## 6. industry_tag 取值约束

`industry_tag` 服务于功能词类型按行业过滤前缀词("按行业调用",PRD 4.1.2)。

**严格约束**:`industry_tag` 仅在 `function.prefix` 必填,**其他场景应为 NULL**:

| 场景 | 必填 | 取值 |
|---|---|---|
| `function.prefix` | ✅ | `common` / `door_window` / `appliance` / `building_material` / `fmcg` / `industrial` / `clothing` |
| 其他 (type, affix_kind) | ❌ 必空 | NULL |

阶段一审计成果:
- function.prefix 43 条 enabled 词全部带 industry_tag
- common 19 条(通用性能/新旧来源/产品分级/规模)
- 6 个具体行业各 4 条

### 取值含义

| industry_tag | 行业 | 备注 |
|---|---|---|
| `common` | 通用 | 不限行业,所有 function 类型查询都返回 |
| `door_window` | 门窗 | 隔音的/防盗的/保温的/抗风的 |
| `appliance` | 家电 | 节能的/静音的/智能的/变频的 |
| `building_material` | 建材 | 环保的/防水的/阻燃的/抗菌的 |
| `fmcg` | 快消 | 健康的/有机的/纯天然的/无添加的 |
| `industrial` | 工业品 | 高精度的/高产能的/稳定运行的/自动化的 |
| `clothing` | 服装 | 百搭的/显瘦的/舒适的/透气的 |

未来扩展行业时,需要前后端联调:
- 后端 INSERT 新 industry_tag 的词
- 前端 KeywordTypeConfigVO 或维护页提供新 industry 选项

阶段一不扩展。

---

## 7. scope_type / scope_id 取值约束

`scope_type` + `scope_id` 服务于临时词的作用域控制(阶段一对齐纪要议题 5.5)。

**核心规则**:`scope_type` 和 `scope_id` 必须配对使用,且 `is_temporary=1` 是触发条件。

| is_temporary | scope_type | scope_id | 含义 |
|---|---|---|---|
| 0 | NULL | NULL | 永久词(种子词/审批通过的永久手动词) |
| 0 | `global` | NULL | 等价于上一行,但显式声明为全局 |
| 1 | `global` | NULL | 全局临时词(很少用,管理员特批用) |
| 1 | `company` | bigint(company_id) | 客户级临时词,只对该客户的所有项目可见 |
| 1 | `project` | bigint(project_id) | 项目级临时词,只对该项目可见 |

**禁止组合**:
- `is_temporary=1` 且 `scope_type IS NULL` → 非法,临时词必须有作用域
- `scope_type IN ('company','project')` 且 `scope_id IS NULL` → 非法,作用域 ID 缺失
- `scope_type='global'` 且 `scope_id IS NOT NULL` → 非法,全局作用域不应有 ID

阶段一暂未实施数据库 CHECK 约束,需在 Service 层加 validation。

阶段一对齐纪要 5.5 的前端文案:
- `scopeType=company` 时显示"客户ID: scopeId"
- 阶段二接公司名映射后改为公司名

---

## 8. options 接口的过滤条件标准

`KeywordAffixWordService.listOptionWords` 实现的过滤条件等价 SQL:

```sql
SELECT * FROM keyword_affix_word
WHERE enabled = 1                                        -- 软删除过滤
  AND type = :type                                       -- 6 类型之一
  AND affix_kind IN ('area','prefix','suffix','industry','compare')
  AND (is_manual = 0 OR approval_status = 'approved')    -- 种子词 OR 已审批临时词
ORDER BY affix_kind, sort_order, id;
```

之后内存里再做 2 层过滤:
1. **手动词作用域过滤**(`isVisibleByManualScope`)
   - 非手动词(is_manual=0)→ 全部保留
   - 手动词(is_manual=1)且 includeManual=false → 全部丢弃
   - 手动词且 is_temporary=0 → 保留(永久手动词,所有人可见)
   - 手动词且 is_temporary=1:
     - `scope_type='global'` → 保留
     - `scope_type='company'` 且 `scope_id == 当前请求的 companyId` → 保留
     - `scope_type='project'` 且 `scope_id == 当前请求的 projectId` → 保留
     - 其他 → 丢弃

2. **行业标签过滤**(`isVisibleByRequestedIndustryTag`)
   - 词的 industry_tag 为空 → 保留(非 function.prefix 词都走这里)
   - industry_tag='common' → 保留
   - industry_tag = 当前请求的 industryTag → 保留
   - 其他(industry_tag 不匹配且非 common) → 丢弃

**重要的边界行为**:`requestedIndustryTag=null` 时,industry_tag='common' 仍然保留。这是 function.prefix 在 industryTag 未选择时仍能展示通用前缀词的设计依据。

---

## 9. 跨 type 同名词的语义

由于唯一键是 `(type, affix_kind, word_text)`,同一个 word_text 可以在不同 type 下重复出现。这是**有意的设计**,每条同名词代表不同的"用户搜索意图":

| word_text | 在哪些 (type, affix_kind) | 各自语义 |
|---|---|---|
| `进口` | brand.prefix / function.prefix / qa.prefix | brand:了解进口品牌 / function:按性能筛选进口 / qa:问进口产品的功能 |
| `国产` | brand.prefix / function.prefix / qa.prefix | 同上 |
| `新款` | function.prefix / qa.prefix | function:按新旧筛选 / qa:问新款功能 |
| `最新款` | function.prefix / qa.prefix | 同上 |
| `靠谱的` | brand.prefix / decision.prefix | brand:了解靠谱品牌 / decision:决策时筛选靠谱选项 |
| `专业的` | brand.prefix / decision.prefix | 同上 |
| `评价高的` | brand.prefix / decision.prefix | 同上 |
| `优秀的` | brand.prefix / decision.prefix | 同上 |
| `品牌` | brand.industry / function.industry / transaction.industry / competitor.industry | 不同搜索场景下的主体词 |
| `厂家` | 多类型 industry | 同上 |
| `产品` | brand.industry / decision.industry / transaction.industry / comparison.industry / competitor.industry | 同上 |
| `怎么选` | decision.suffix / comparison.suffix | decision:常规决策 / comparison:对比式决策 |
| `注意事项` | decision.suffix / qa.suffix(qa 类未 INSERT,跳过避免重复) | decision:决策时的注意 / qa:产品使用注意 |

options 接口按 `type` 过滤,自动只返回当前 type 下的那一份,不会出现"同一词多份显示"的问题。

维护页编辑同名词时,**必须用 id 区分**,不能仅用 word_text + type 推断(因为还有 affix_kind 的差异)。

---

## 10. 历史类型(location/industry/competitor/search)的字段处理

历史类型词在 V90 之后的处理原则:

| 字段 | 历史类型词的取值 | 备注 |
|---|---|---|
| `enabled` | 大部分保留 1,占位符词全部 0 | 老组继续可执行依赖词库保留 |
| `sub_category` | 全部 NULL | 历史类型不在新 UI 渲染,无需分组 |
| `visual_tag` | 全部 NULL | 不适用 |
| `industry_tag` | 全部 NULL | 不适用 |
| `is_manual / is_temporary` | 0 / 0 | 全是种子词 |
| `approval_status` | approved | 老数据默认状态 |

**options 接口对历史类型的处理**:
- options 接口要求 type 必须是 6 个新类型之一,**历史类型不会被 options 查询命中**
- 但 KeywordAffixWordService.ensureTypeExists 接受历史类型(走 LEGACY_TYPES 校验)以支持老组编辑/查看

**新建场景对历史类型的处理**:
- 类型选择器只展示 6 个新类型 + 当前编辑词组的历史类型(置灰)
- 用户不能新建历史类型词组
- 维护页(KeywordAffixWordAdminController)不限制 type,可以编辑历史类型词,但建议管理员**只下架不新增**

---

## 11. 字段约束对应到代码的 validation 注解建议

下面是给 codex 写代码时贴在 entity / DTO 上的 validation 注解建议(阶段二实施完整 validation 时参考):

```java
@Data
@TableName("keyword_affix_word")
public class KeywordAffixWord {
    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank
    @Pattern(regexp = "^(brand|decision|transaction|comparison|qa|function|location|industry|competitor|search)$")
    private String type;

    @NotBlank
    @Pattern(regexp = "^(area|prefix|industry|suffix|compare)$")
    private String affixKind;

    @Size(max = 50)
    private String subCategory;

    /** 仅 decision.industry / function.industry 必填 */
    @Pattern(regexp = "^(toB|toC|common)$", message = "visual_tag 必须是 toB/toC/common")
    private String visualTag;

    /** 仅 function.prefix 必填 */
    @Pattern(regexp = "^(common|door_window|appliance|building_material|fmcg|industrial|clothing)$")
    private String industryTag;

    @NotBlank
    @Size(max = 255)
    private String wordText;

    @NotNull
    @Min(0)
    private Integer sortOrder;

    @NotNull
    private Boolean enabled;

    @NotNull
    private Boolean isManual;

    @NotNull
    private Boolean isTemporary;

    @Pattern(regexp = "^(company|project|global)$")
    private String scopeType;

    private Long scopeId;

    private LocalDateTime lastUsedAt;

    private Long addedByUserId;

    @NotBlank
    @Pattern(regexp = "^(pending|approved|rejected)$")
    private String approvalStatus;

    private String approvalReason;

    private Long approvedBy;

    private LocalDateTime approvedAt;

    @NotNull
    private LocalDateTime createdAt;

    @NotNull
    private LocalDateTime updatedAt;
}
```

更复杂的"按 type+affix_kind 决定字段必填"的跨字段 validation,需要写自定义 ConstraintValidator,阶段二再做。

---

## 12. 修改本文档的流程

本文档是 `keyword_affix_word` 表的字段语义权威。修改流程:

1. 在 `docs/keyword-affix-audit-actions.md` 记录变更动机
2. 同步更新本文档的对应 §
3. 如果涉及 entity/DTO 字段语义变化,跑一遍现有词库的字段一致性 SQL 校验
4. 提交 PR 时引用本文档