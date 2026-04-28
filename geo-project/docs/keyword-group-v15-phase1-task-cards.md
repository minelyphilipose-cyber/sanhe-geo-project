# 拓词管理 V1.5 阶段一任务卡 V2

> 范围：WBS #1 ~ #8，目标完成主链路。总工期口径沿用终版 WBS，阶段一核心链路约 7-9 个工作日；#1.5 与 #5/#7 并行。

## 通用完成定义 DoD

- 代码完成后经过同伴 review，关键决策与接口字段名不得偏离 V90 SQL 与 OpenAPI 契约 V2.1。
- 后端涉及生成器、迁移兼容、黑名单占位逻辑的改动需补充单测或服务层测试。
- 前端涉及主流程和维护页的改动需完成 Chrome 主路径冒烟，必要时补充 Edge 冒烟。
- 任务完成时同步更新相关文档或任务备注，明确未完成项、风险与后续阶段承接点。

## #1 数据库迁移

**标题**：V90 拓词 V1.5 数据库迁移

**描述**：为拓词类型、系统词扩展、地区词迁移、黑名单、项目关联与阶段三配额预留提供数据库基础。范围仅包含 schema、seed、一次性数据修正与开发期回滚脚本，不包含业务代码接入。

**子任务清单**

- [ ] 创建独立字典 `keyword_group_type` 并 seed 6 个新类型。
- [ ] 防御性确认 `sys_dict_item.uk_dict_type_key` 唯一索引存在，缺失时补建。
- [ ] 扩展 `keyword_affix_word` 词库字段与 options/审批相关索引。
- [ ] 扩展 `keyword_group_word.column_type` 代码层枚举语义，并执行 `region -> area` 一次性迁移。
- [ ] 创建 `keyword_group_word_region_backup` 备份表，保留开发期校验与回滚依据。
- [ ] 软下架 `qa.industry` 系统词，并保留备份。
- [ ] 扩展 `keyword_group.area_enabled / function_industry_tag / project_id`。
- [ ] 创建 `blacklist_word` 表，补充标准化字段与唯一索引。
- [ ] 交付开发期备用 `V90_rollback.sql`。

**验收标准**

- V90 脚本可在 V55 前状态库上重复校验执行，字典 seed 使用幂等写法。
- 在已部分执行过 `ALTER` 的开发库上重跑 V90 不应报错，防御性 `IF @col_exists` 与 `IF @idx_exists` 检查行为正确。
- `keyword_group_word.column_type='region'` 数据迁移为 `area`，备份表可查到迁移前数据。
- 老组存在 `area/region` 词时，`keyword_group.area_enabled` 被兜底更新为 `1`。
- `keyword_group.project_id` 字段已预留，阶段三可直接接配额服务。
- `V90_rollback.sql` 作为开发期备用交付，不进入 Flyway 正式迁移链。

**估时**：1.5d

**建议负责人/角色**：后端

**前置依赖**：无

**关联文档**

- `docs/V90__keyword_group_v15_schema_draft.md`
- `docs/keyword-group-v15-openapi-contract.md`

**联调要点**

- 开发期保留 `keyword_group_word_region_backup`，#8 联调完成前不清理。
- 确认 `sys_dict_item(dict_type, dict_key)` 唯一索引存在，避免 seed 多次写入脏数据。

## #1.5 现有词库审计

**标题**：现有拓词词库审计与补齐

**描述**：由 AI 训练师基于现有 `keyword_affix_word` 数据、PRD 新 6 类型词库结构与真实业务样例，审计并补齐一期可用的系统词。范围包含数据质量检查、缺口识别、候选词整理与维护页批量录入验收，不直接改代码。

**子任务清单**

- [ ] 按 `type / affix_kind / sub_category / visual_tag / industry_tag` 审计现有词库覆盖度。
- [ ] 标记历史类型 `location / industry / competitor` 中可迁移复用的词。
- [ ] 整理功能词通用类与行业类前缀词，明确 `industry_tag`。
- [ ] 整理对比词 `compareWords` 候选池。
- [ ] 清理不适合进入系统词库的敏感词、低质词、重复词。
- [ ] 通过 #7 维护页批量导入链路录入审计结果。
- [ ] 输出审计记录与导入错误行处理说明。

**验收标准**

- 6 个新拓词类型均有可用于预览和保存的基础词库数据。
- 功能词至少区分 `common` 与一个业务行业 `industry_tag`，可验证行业切换行为。
- 对比词类型具备 `compareWords` 与后缀词候选，能支撑 #8 对比词联调。
- 批量导入过程触发黑名单前置检查，错误行可回传给维护页展示。
- 截止日为 #7 完成日 + 1 天，确保 #8 可使用真实数据回归。
- 审计完成后，使用预览接口随机抽样 100 条生成结果，AI 训练师自评自然度合格率 >=85%，作为 #8 阶段一验收前置。

**估时**：2-3d

**建议负责人/角色**：AI 训练师

**前置依赖**：#1

**关联文档**

- `docs/V90__keyword_group_v15_schema_draft.md`
- `docs/keyword-group-v15-openapi-contract.md`

**联调要点**

- #8 前至少提供一批真实审计词，避免只用 Mock 数据验证生成器。

## #2 后端 DTO 与类型配置

**标题**：拓词 DTO 扩展与类型配置服务

**描述**：根据接口契约 V2.1 落地后端请求/响应结构，新增集中式 `KeywordTypeConfig`，让前端和生成器共享同一套 6 类型规则。范围包含 DTO/VO、类型配置、基础校验规则，不包含生成器重构。

**子任务清单**

- [ ] 扩展 `KeywordGroupColumnsRequest`，新增 `coreWordsA / compareWords / coreWordsB`。
- [ ] 新增或调整 `KeywordWordItemRequest / KeywordWordItemVO / KeywordGroupColumnsVO`。
- [ ] 新增 `KeywordTypeConfig` 配置类，包含 `columns / requiredColumns / areaEnabledByDefault / industryRequired / functionIndustryRequired / supportsManualAdd / structure`。
- [ ] 扩展 `KeywordAffixWordOptionVO`，返回 `typeConfigs / currentTypeConfig / areaWords / compareWords`。
- [ ] 补充业务错误码枚举或异常常量。
- [ ] 更新 Swagger/OpenAPI 注解，与契约 V2.1 保持一致。

**验收标准**

- 6 类型配置由后端统一返回，前端不需要硬编码一套类型规则。
- `KeywordGroupVO.columns` 使用 VO 结构，返回词项包含 `id`。
- 所有词列字段返回空数组 `[]`，不返回 `null`。
- 对比词 request/VO 能完整表达 `coreWordsA × compareWords × coreWordsB × suffixWords`。
- OpenAPI 文档与代码字段命名一致，无中途改名。

**估时**：1d

**建议负责人/角色**：后端

**前置依赖**：#1

**关联文档**

- `docs/keyword-group-v15-openapi-contract.md`

**联调要点**

- `industryRequired` 与 `functionIndustryRequired` 是两个不同字段，校验和 options 加载不要混用。

## #3 后端生成器重构

**标题**：拓词生成器稳定化与对比词生成

**描述**：重构现有拓词生成逻辑，去除随机 `shuffle`，按稳定顺序生成关键词，并新增对比词四列组合生成器。范围包含预览、保存候选池校验及生成后字段映射。

**子任务清单**

- [ ] 梳理现有普通生成器，保留地区/前缀/核心/行业/后缀组合逻辑。
- [ ] 移除随机 `shuffle`，改为候选池组合顺序或自然排序，保证同输入同输出。
- [ ] 新增对比词生成器：`coreWordsA × compareWords × coreWordsB × suffixWords`。
- [ ] 按 `KeywordTypeConfig.requiredColumns` 同步候选池校验。
- [ ] 保存链路校验 `resultKeywords` 必须来自预览候选池。
- [ ] 补充单元测试或服务层测试覆盖普通类型和对比类型。

**验收标准**

- 同一 payload 多次预览结果完全一致。
- 对比词 payload 使用新字段，不借用 `coreWords / industryWords`。
- `coreWordsA / compareWords / coreWordsB` 缺失分别返回约定错误或基础参数校验。
- 保存非候选池关键词返回 `INVALID_RESULT_KEYWORDS`。
- 老类型组进入预览/保存路径时，生成器按 V90 兼容映射读取 `region/area`，不因 `column_type` 枚举重构丢失老数据。

**估时**：1.5d

**建议负责人/角色**：后端

**前置依赖**：#2

**关联文档**

- `docs/keyword-group-v15-openapi-contract.md`

**联调要点**

- 生成器排序键要写在代码注释中，#8 用同输入多次预览验证。

## #4 拓词页 Options 接口改造

**标题**：拓词页词库 Options 接口扩展

**描述**：仅改造拓词页使用的 `/api/keyword-affix-words/options`，支持新类型配置、功能词行业标签、手动词作用域与对比词候选池。范围不扩展其他业务字典接口。

**子任务清单**

- [ ] 增加入参 `industryTag / includeManual / scopeType / scopeId`。
- [ ] 返回 `typeConfigs / currentTypeConfig`。
- [ ] 返回 `areaWords / prefixWords / industryWords / suffixWords / compareWords`。
- [ ] options 查询条件统一为 `enabled=1 AND (is_manual=0 OR approval_status='approved')`。
- [ ] `includeManual=true` 时按 `scopeType/scopeId` 返回可见临时词。
- [ ] 功能词按 `common + 当前 industryTag` 加载前缀词。

**验收标准**

- `qa.industry` 软下架后前端 options 不再展示。
- `includeManual=false` 不返回临时词。
- `includeManual=true` 返回临时词时带 `isTemporary / scopeType / scopeId`。
- 切换客户后不展示其他客户 `scope_type='company'` 的临时词。
- 功能词切换行业时，通用类保留，行业类替换。

**估时**：0.5d

**建议负责人/角色**：后端

**前置依赖**：#1、#2

**关联文档**

- `docs/keyword-group-v15-openapi-contract.md`

**联调要点**

- 本卡范围仅拓词页 options 接口，不触碰全局字典或问题池字典接口。

## #5 前端组件拆分与主流程

**标题**：拓词管理页组件化与 6 类型主流程

**描述**：重构 `KeywordGroupManage.vue`，按 6 类型配置驱动页面渲染，拆分普通五列和对比词四列组件，同时保证编辑回填和类型切换没有清空副作用。范围包含创建、编辑、预览、保存主流程，不包含维护页。

**子任务清单**

推荐推进顺序：先接类型配置与 Mock 数据，再拆普通列和对比列组件，最后处理编辑回填与历史类型兼容。

- [ ] 新增 `KeywordTypeSelector`，展示 6 新类型并兼容历史类型标识。
- [ ] 新增 `KeywordColumnBuilder`，承载普通结构五列。
- [ ] 新增 `CompareKeywordBuilder`，承载对比词四列。
- [ ] 新增 `KeywordWordColumn`，支持单列展示、子分类分段与手动词浅黄色标识。
- [ ] 实现类型切换 `change-by-user` 事件机制，区分用户主动操作与编辑回填。
- [ ] 实现 `lastEditState` 副本机制：用独立 `ref` 保存类型快照，切换时通过深拷贝 `snapshot/restore`，避免 `form` 与快照共享引用。
- [ ] 编辑回填路径只调用 `loadOptionsByType`，不触发 `clearTypeRelatedSelections`。
- [ ] 接入 Mock 或后端 options/preview/save 契约数据。

**验收标准**

- 6 个新类型均可进入创建、预览、保存流程。
- `brand / qa` 不展示地区词列；地区开关只在列展示时出现。
- 对比词类型提交 `coreWordsA / compareWords / coreWordsB / suffixWords`。
- 从历史 `search` 切到 `comparison` 弹确认并清空 `coreText`，切回历史类型可通过 `lastEditState` 恢复。
- 编辑已有组时不触发 `clearTypeRelatedSelections`，不清空前缀/行业/后缀选项。
- 功能词行业切换时通用性能类常驻，行业类替换。

**估时**：3.5d

**建议负责人/角色**：前端

**前置依赖**：#2；可使用 Mock 与 #3/#4 并行

**关联文档**

- `docs/keyword-group-v15-openapi-contract.md`

**联调要点**

- `columns` 与 `requiredColumns` 都来自后端配置；前端只做渲染和基础提示，不再维护第二套类型规则。

## #6 拓词组列表小调整

**标题**：拓词组列表新老类型展示调整

**描述**：调整 `/api/keyword-groups` 列表页和前端展示，让新 6 类型作为筛选主入口，历史类型继续可见但带“历史”标识。范围包含列表字段、筛选项和保存关键词数量展示。

**子任务清单**

- [ ] 列表接口返回 `PageResult<KeywordGroupListItemVO>`，不返回 `columns`。
- [ ] 列表 VO 增加 `projectId / projectName / packageType / typeLabel / legacyType / savedKeywordCount`。
- [ ] 类型筛选只展示新 6 类型。
- [ ] 历史类型 `search / location / industry / competitor` 列表展示“历史”标。
- [ ] 前端展示保存关键词数与项目信息。

**验收标准**

- 新 6 类型可正常筛选。
- 历史类型组不从列表消失，并能清晰标识“历史”。
- 列表接口不返回完整 `columns`，避免不必要 payload。
- 保存关键词数与详情页数量口径一致。

**估时**：0.5d

**建议负责人/角色**：后端 + 前端

**前置依赖**：#2、#5

**关联文档**

- `docs/keyword-group-v15-openapi-contract.md`

**联调要点**

- `type` 筛选保持单选，与现有 `getKeywordGroupPage` 风格一致。

## #7 词库维护页改造

**标题**：系统词维护页扩展与批量导入

**描述**：改造 `/admin/settings/affix-words` 维护页，支持新字段、新筛选、审批字段展示、维护接口扩展与 Excel 批量导入。范围包含维护页前端、维护页后端接口扩展、模板下载、上传解析、黑名单前置检查和错误行提示。

**子任务清单**

- [ ] 列表新增 `subCategory / visualTag / industryTag / isManual / isTemporary / approvalStatus` 等字段展示。
- [ ] 新增筛选项：`type / affixKind / subCategory / visualTag / industryTag / approvalStatus`。
- [ ] 表单新增 `subCategory / visualTag / industryTag / approvalStatus / approvalReason` 等字段。
- [ ] 扩展维护页新增/编辑/删除接口，适配 V90 新字段。
- [ ] 实现简化版 `BlacklistChecker`：读 `blacklist_word` 表 + 子串匹配，给批量导入用，作为阶段二完整黑名单服务的占位。
- [ ] 增加审批状态相关操作入口，审批主流程由 #11 承接。
- [ ] 实现 Excel 模板下载。
- [ ] 实现 Excel 上传解析、黑名单前置检查与错误行高亮提示。
- [ ] 支持 #1.5 词库审计结果直接批量录入。

**验收标准**

- 新字段可查询、展示、编辑并正确保存。
- 批量导入能返回成功条数和错误行明细。
- 导入命中黑名单时阻断对应行，错误行可定位到词与原因。
- #1.5 审计数据可不经中间转换直接通过维护页导入。
- 维护页接口扩展不影响拓词页 options 查询语义。

**估时**：3.5d

**建议负责人/角色**：前端 + 后端

**前置依赖**：#1、#2；阶段一内联简化版黑名单（读 `blacklist_word` + 子串匹配，无缓存/无短词保护），阶段二 #9 替换为完整服务。

**关联文档**

- `docs/V90__keyword_group_v15_schema_draft.md`
- `docs/keyword-group-v15-openapi-contract.md`

**联调要点**

- 批量导入是 #1.5 真实词库审计进入系统的主通道，#8 前需要至少完成一轮真实数据导入。

## #8 阶段一联调与回归

**标题**：阶段一主链路联调回归

**描述**：对 V90 数据迁移、后端类型配置、生成器、options、前端主流程、列表、维护页进行阶段一集成验收。范围只覆盖主链路，不引入阶段二手动添加、审批通知、生成后黑名单过滤的额外回归面。

**子任务清单**

- [ ] 准备 V55 前与 V90 后两套可重置测试数据。
- [ ] 执行老数据 4 个场景回归。
- [ ] 执行新数据 8 个场景回归。
- [ ] 验证 V90 迁移备份表与 `area_enabled` 兜底数据。
- [ ] 使用 #1.5 审计词库进行真实数据预览与保存。
- [ ] 汇总缺陷、阻塞项与阶段二前置修复清单。

**验收标准**

- 老组打开编辑不丢词，不因 `region -> area` 或类型配置改造报错。
- 6 个新类型预览和保存稳定可复现。
- 对比词 payload 与详情回填字段正确。
- 功能词行业切换、临时词作用域、软下架词库展示符合约定。
- 阶段一验收通过前不启动阶段二，避免黑名单和手动添加扩大回归面。

**估时**：1.5d

**建议负责人/角色**：后端 + 前端 + QA/产品验收

**前置依赖**：#1、#2、#3、#4、#5、#6、#7、#1.5

**关联文档**

- `docs/V90__keyword_group_v15_schema_draft.md`
- `docs/keyword-group-v15-openapi-contract.md`

**联调要点：老数据场景**

- [ ] `type=search` 的老组打开编辑，不切类型直接保存，不报错；`area_enabled` 由 V90 兜底 UPDATE 写入的值正确读取并保留。
- [ ] `type=location / industry / competitor` 的老组列表展示，并标“历史”。
- [ ] `column_type='region'` 的老数据被正确读取为 `area`。
- [ ] 老组打开编辑，切到新类型 `comparison`，再切回原类型，数据通过 `lastEditState` 副本恢复。

**联调要点：新数据场景**

- [ ] 6 个新类型各建一个拓词组，完成预览 + 保存全流程。
- [ ] 对比词类型的 `coreWordsA / compareWords / coreWordsB / suffixWords` payload 正确。
- [ ] 功能词行业切换时，`common` 通用类不清空，行业类替换。
- [ ] 临时手动词 `scope_type='company' + scope_id=X`，切换客户后不显示。
- [ ] `qa.industry` 软下架后前端不展示。
- [ ] 同输入多次预览，结果一致。
- [ ] 历史 `search` 切到 `comparison` 弹确认，并清空 `coreText`。
- [ ] 编辑回填路径不触发 `clearTypeRelatedSelections`。
