# 拓词词库人工审计动作记录

> 用途：记录 `keyword_affix_word` 表在阶段一词库审计前的人工/工程处理动作。当前表无 `remark` 字段，因此用本文档替代行级备注；`remark` 字段后续随阶段二维护页/审批字段一起补充。

## 2026-04-28

### 1. 软下架广告法高风险绝对化用语

| id | type | affix_kind | word_text | 动作 | 理由 |
|---:|---|---|---|---|---|
| 297 | brand | prefix | 最好的 | `enabled=0` | 违反《广告法》第九条绝对化用语禁令，高风险词立即下架 |

执行说明：原计划写入 `remark='[auto-disabled: 违反广告法绝对化用语]'`，但当前 `keyword_affix_word` 表无 `remark` 字段，故仅执行软下架并在本文档记录。

### 2. 修复对比词后缀占位符

| id | type | affix_kind | 原 word_text | 新 word_text | 动作 | 理由 |
|---:|---|---|---|---|---|---|
| 602 | comparison | suffix | 和XX哪个好 | 哪个更好 | 修改词文本 | `XX` 不会被生成器替换，会作为字面量输出 |

### 3. 软下架历史竞品词占位符

| id | type | affix_kind | word_text | 动作 | 理由 |
|---:|---|---|---|---|---|
| 633 | competitor | prefix | 比XX更好的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 634 | competitor | prefix | XX的替代品 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 635 | competitor | prefix | 不输XX的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 636 | competitor | prefix | 超越XX的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 637 | competitor | prefix | 跟XX类似的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 638 | competitor | prefix | 对标XX的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 639 | competitor | prefix | PK掉XX的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 640 | competitor | prefix | 可以替代XX的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 641 | competitor | prefix | XX的平替 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 642 | competitor | prefix | 和XX竞争的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 643 | competitor | prefix | XX的竞争对手 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 644 | competitor | prefix | 比XX便宜的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 645 | competitor | prefix | 比XX好用的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 646 | competitor | prefix | 比XX专业的 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 655 | competitor | suffix | 为什么选它不选XX | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 656 | competitor | suffix | 从XX迁移过来 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 657 | competitor | suffix | 比XX好在哪 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 658 | competitor | suffix | 和XX的核心差异 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 659 | competitor | suffix | 能否替代XX | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 660 | competitor | suffix | 相比XX的优势 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |
| 664 | competitor | suffix | 用户从XX转过来的体验 | `enabled=0` | 历史类型占位符词，生成器不会替换 `XX` |

### 4. 明确保留的合规词

| id | type | affix_kind | word_text | 动作 | 理由 |
|---:|---|---|---|---|---|
| 365 | location | prefix | 离我最近的 | 保留 | “最近”是地理事实描述，不是商业评价 |
| 563 | transaction | suffix | 最新价格 | 保留 | “最新”是时间性描述，合规 |

### 5. 二次高风险绝对化词扫描

扫描规则覆盖：`最佳 / 最优 / 最强 / 最专业 / 最权威 / 最先进 / 顶级 / 顶尖 / 绝无仅有 / 空前 / 绝对 / 完美 / 极致 / 王者 / 霸主`。

结果：启用词中未命中上述高风险候选。

## 2026-04-28 批 0:无争议基础修正

### 1. 新增 comparison.compare 连接词

- INSERT 6 条:和 / 对比 / 与 / VS / 比较 / 相比
- 来源:PRD 3.4.4

### 2. 新增 function 类型完整词库

- function.prefix:32 条,7 个 industry_tag(common 8 条 + 6 行业各 4 条)
- function.industry:5 条(品牌/厂家/型号/款式/产品)
- function.suffix:7 条(推荐/哪个好/哪款好/选什么/有哪些/什么牌子好/推荐一下)
- 来源:PRD 3.6
- 备注:"(留空)"按设计不入库,生成器对空列按 [''] 处理

### 3. qa 类型错位词大迁移

- qa.prefix:28 条决策风格前缀词迁入 decision.prefix(sort_order +1000)
  - 迁移 id 列表: 9-22, 24, 26-38(共 28 条)
  - 保留 id=23(性价比高的)、id=25(做得好的),待批 5 进一步评估
- qa.suffix:29 条决策风格后缀词迁入 decision.suffix(sort_order +1000)
- qa.suffix 重复词 id=47(怎么选)未迁移,因 decision.suffix 已存在同名词 id=494,为避免唯一键冲突已将源 qa 行软下架
- 理由:这些词全部是"哪家好/靠谱的"决策意图,留在 qa 类型下会让问答词类型生成出完全不符合问答意图的关键词
- 影响:qa 类型 enabled prefix/suffix 几乎清空,批 5 会重建为真正的问答词(进口/国产/怎么用/坏了怎么办等)

### 4. 执行后验证

- function: industry=5, prefix=32, suffix=7
- comparison.compare=6
- qa: prefix=2, suffix=0
- decision: industry=20, prefix=48, suffix=52

## 2026-04-28 批 1:brand 品牌词类型审计

### 1. brand.prefix 处理

- 保留 22 条,补 sub_category(正向评价类 3 / 品质特征类 10 / 来源类 3 / 规模类 1 / 资质类 5)
- 下架 2 条:id=296"十大"(与"排名前十的"重复)、id=313"小众但好用的"(词义过长不自然)
- INSERT 4 条正向评价类:靠谱的 / 专业的 / 评价高的 / 优秀的

### 2. brand.industry 处理

- 保留 6 条:品牌/厂家/公司/集团/老字号/工作室,补 sub_category=主体词或品质特征
- 下架 1 条:id=71"厂商"(与"厂家"重复)
- 迁入 decision.industry 13 条(sort_order +2000):
  - toB 7 条:企业/供应商/制造商/生产商/运营商/开发商/服务商
  - toC 6 条:商家/代理商/经销商/连锁店/旗舰店/专卖店
- INSERT 2 条主体词:产品/这个牌子

### 3. brand.suffix 大改造

- 现状 24 条几乎全是决策风格(品牌排行榜/十大品牌/哪个品牌好...),与 PRD 3.1.5 期望的"了解/评价/归属"完全不符
- 保留 2 条:id=329"品牌有哪些"、id=346"老牌子有哪些"(归入了解类)
- 下架 2 条:id=333"品牌top10"(重复)、id=337"知名品牌"(非后缀语法)
- 迁入 decision.suffix 20 条(sort_order +2000):
  - 榜单类 7 / 推荐类 3 / 决策选择类 6 / 测评类 2 / 对比类 1 / 指南类 1
- INSERT 15 条 PRD 3.1.5 期望的标准品牌后缀:
  - 了解类 6:怎么样/是什么/介绍/简介/好不好/有名吗
  - 评价类 5:口碑如何/靠不靠谱/质量怎么样/评价/值不值得买
  - 归属类 4:是哪里的/是哪个公司的/是什么牌子/哪里生产的

### 4. 影响

- brand 类型重新对齐 PRD 3.1 设计,从"决策词混在 brand"还原为真正的"了解品牌"意图
- decision 类型词库进一步丰富:industry +13 条(带 visual_tag)、suffix +20 条
- 后续批次会基于此继续审计 decision 类型

### 5. 执行后验证

- brand: industry=8, prefix=26, suffix=17
- decision: industry=33, prefix=48, suffix=72
- brand.suffix enabled 词均为了解类/评价类/归属类
- brand enabled 词 sub_category NULL 数量=0

## 2026-04-28 批 2:decision 决策词类型审计

### 1. decision.prefix 原 20 词处理

- 保留 7 条:值得选的/推荐的/适合的/合适的/理想的/首选的(正向评价类)+ 优质的(品牌品质类)
- 迁入 transaction.prefix(sort_order +3000):
  - 性价比类 2:划算的(id=470)、高性价比的(id=471)
  - 价格敏感类 1:免费的(id=472)
- 迁入 function.prefix(sort_order +3000, industry_tag=common):
  - 产品分级类 7:入门级/进阶版/旗舰/基础版/企业级/轻量级/全功能(id=473-477,481,482)
  - 规模类 3:小型/中型/大型(id=478,479,480)

### 2. decision.prefix 批 0 迁入 28 词处理

- 保留 16 条,补/调整 sub_category(正向评价类/品牌品质类/资质类/场景类)
- 下架 12 条:
  - 重复:有名的(17)、口碑不错的(27)
  - 语义模糊:比较好的(10)
  - 语法不通("含想找/有没有/求推荐"系列):29,30,31
  - "比较 XX 的"系列(作为前缀修饰不自然):33,34,35,36,37,38
- INSERT 6 条 PRD 期望的品牌品质类:一线的/大牌的/老牌的/头部的/高端的/中高端的

### 3. decision.industry 原 20 词处理

- 保留 15 条,补 sub_category=行业词 + visual_tag(common 5 / toC 1 / toB 9)
- 下架 3 条:项目(172)/计划(173)/模式(174),作行业词不自然
- 迁入 function.industry(sort_order +3000, visual_tag=common):
  - 版本(175)/配置(176)

### 4. decision.suffix 处理

- 原 23 词全部保留,补 sub_category(决策选择类 7 / 指南类 4 / 注意事项类 4 / 决策依据类 4 / 建议类 4)
- 批 0 迁入下架 4 条重复:哪家比较好(48,与 39 重复)/哪家更专业(51,与 44 重复)/哪家更靠谱(52,与 41 重复)/哪家更正规(57,冗余)
- 批 1 迁入 20 条品牌相关榜单/推荐/对比词:全部保留,不动 sub_category

### 5. 影响

- decision 类型全面对齐 PRD 3.2 设计
- transaction.prefix +3 条扩展(批 3 会进一步审计 transaction)
- function.prefix +10 条产品分级/规模类、function.industry +2 条版本配置(批 6 会综合 function 审计)

### 6. 执行后验证

- decision: prefix=29, industry=28, suffix=68
- transaction.prefix 迁入 id=470,471,472 共 3 条
- function.prefix 迁入 id=473-482 共 10 条
- function.industry 迁入 id=175,176 共 2 条
- decision enabled 词 sub_category NULL 结果为空
- decision.industry enabled 词 visual_tag NULL 数量=0

## 2026-04-28 批 3:transaction 成交词类型审计

### 1. transaction.prefix 处理

- 保留 19 条原 + 3 条批 2 迁入,补 sub_category:
  - 价格敏感类 3:便宜的(525)/实惠的(526)/低价(534)
  - 优惠活动类 10:打折的/优惠的/特价/限时/包邮/促销/清仓/秒杀/会员价/新人专享
  - 渠道类 2:团购(531)/批发(532)
  - 渠道保障类 4:正品/官方/直营/厂家直销
- 下架 1 条:id=533"免费试用"(与批 2 迁入的"免费的"重复,且作前缀语法笨重)
- INSERT 2 条 PRD 期望:平价的(价格敏感类)/性价比好的(性价比类)

### 2. transaction.industry 处理

- 保留 18 条,补 sub_category:
  - 通用主体 4:服务/商品/产品/课程
  - 商业模型类 14:套餐/会员/订阅/许可证/授权/定制/租赁/托管/外包/代运营/年卡/月卡/体验装/试用版
- 下架 1 条:id=193"报价"(作行业词不通)
- 迁出 1 条到 function.industry(sort_order +3000):
  - id=212"企业版"(产品分级,与"基础版/旗舰版"同类)
- INSERT 4 条 PRD 期望:品牌/厂家/型号/款式

### 3. transaction.suffix 处理

- 保留 20 条原,补 sub_category:
  - 价格查询类 10:多少钱/价格表/收费标准/怎么收费/报价单/费用明细/最新价格/套餐价格/年费多少/月费多少
  - 预算对比类 2:价格对比(562)/性价比分析(577)
  - 优惠类 4:优惠活动/折扣信息/促销价/团购价
  - 购买渠道类 4:在哪买/怎么买/购买渠道/订购方式
- 下架 2 条不自然词:id=568"免费试用入口"(过长)、id=572"官方购买链接"(过长)
- 迁出 3 条到 qa.suffix(sort_order +3000):
  - 攻略类 2:砍价技巧(578)/省钱攻略(579)
  - 注意事项类 1:隐藏收费项(580)
- INSERT 12 条 PRD 3.3.5 期望补全:
  - 价格查询类 5:什么价格/价格/价位/一般多少钱/大概多少钱
  - 预算对比类 4:贵不贵/值不值/划不划算/性价比怎么样
  - 购买渠道类 3:去哪买/哪里能买到/哪里有卖

### 4. 影响

- transaction 类型对齐 PRD 3.3 设计,价格查询/预算对比/购买渠道三类齐备
- function.industry +1 条(企业版)
- qa.suffix +3 条攻略/注意事项类,首次有真正的问答风格词进入(批 5 会进一步重建 qa)

### 5. 执行后验证

- transaction: prefix=24, industry=22, suffix=32
- transaction enabled 且 sub_category IS NULL = 0
- function.industry +1(id=212 企业版)
- qa.suffix +3(id=578,579,580)
## 2026-04-28 批 4:comparison 对比词类型审计

### 1. 架构原则
- PRD 4.1.2 + 契约 V2.1 规定:对比词类型的 UI columns 配置只渲染 suffix + compareCore + compareWord
- prefix / industry 两列不渲染,留在表里的词永远不会被生成器使用
- 因此 comparison.prefix 和 comparison.industry 的所有词应全部软下架,与 V90 软下架 qa.industry 同一道理

### 2. comparison.prefix 全部下架
- 14 条全部 enabled=0(id 587-600)
- 词内容:更好的/替代/类似/同类/升级版/平替/对标/竞争/同价位/同级别/国产替代/开源替代/免费替代/低成本替代
- 这些词本身有商业价值,但对比词类型架构上不需要 prefix,留着是死数据

### 3. comparison.industry 全部下架
- 20 条全部 enabled=0(id 224-243)
- 词内容:产品/方案/平台/工具/软件/系统/服务/机构/品牌/供应商/框架/技术/模式/标准/协议/开源方案/商业方案/SaaS/本地部署/混合方案
- 同样架构原因下架

### 4. comparison.suffix 处理
- 保留 22 条原词,补 sub_category:
  - 对比选择类 4:哪个更好/区别是什么/有什么不同/差异在哪里
  - 对比类 6:对比分析/横向对比/全方位对比/深度对比/实测对比/同类产品对比
  - 维度对比类 5:性能对比/价格对比/功能对比/体验对比/兼容性对比
  - 优劣分析类 5:优缺点对比/哪个更值得/怎么选/各有什么优势/谁更胜一筹
  - 替代类 2:替代方案有哪些/平替推荐
- 迁出 2 条到 qa.suffix(注意事项类,sort_order +4000):
  - 迁移成本高吗(623)/切换难度大吗(624)
- INSERT 5 条 PRD 3.4.5 期望补全:
  - 对比选择类 1:差别
  - 优劣分析类 4:哪个值得买/哪个性价比高/哪个更靠谱/哪个更推荐

### 5. comparison.compare 无动作
- 批 0 已 INSERT 6 条连接词,sub_category=对比连接词,本批无需处理

### 6. 影响
- comparison 类型对齐 PRD 3.4 设计,只保留 suffix + compare 两列
- prefix(14) + industry(20) 共 34 条死数据软下架,可在批 8 一起评估是否物理删除
- qa.suffix +2 条注意事项类(批 5 会重建 qa)

### 7. 执行后验证
- comparison: prefix=0, industry=0, suffix=27, compare=6
- 注:原批次说明里写 suffix=26 是算术口径少算 1 条;实际原 suffix 24 条,迁出 2 条后保留 22 条,再 INSERT 5 条,所以应为 27
- comparison enabled 且 sub_category IS NULL = 0
- qa.suffix +2(id=623, 624),当前 qa.suffix=5

## 2026-04-28 批 5:qa 问答词类型重建

### 1. 原 qa.prefix 2 条最终决策
- id=23"性价比高的" 迁入 transaction.prefix(性价比类,sort_order +4000)
  - 修正前期决策:之前批 0 留作"待评估",经审计认定语义偏价格意图,不属问答
- id=25"做得好的" 软下架
  - 词义模糊,作问答前缀生成"做得好的XX怎么用"语法笨重

### 2. qa.prefix 重建 5 条 INSERT(PRD 3.5.4)
- 来源类 2:进口 / 国产
- 新旧类 2:新款 / 最新款
- 品质类 1:高品质
- 跨 type 共存:进口/国产/新款/最新款 在 function.prefix 也有,允许两个 type 共存
  - 语义角色不同:qa 表示"问产品的来源/新旧"(进口XX怎么用),function 表示"按性能筛选"(进口XX推荐)

### 3. qa.suffix 重建 14 条 INSERT(PRD 3.5.5)
- 使用类 4:怎么用 / 使用方法 / 安装方法 / 使用教程
- 维护类 4:怎么保养 / 保养方法 / 维护 / 清洁方法
- 故障类 4:坏了怎么办 / 维修 / 售后 / 质保
- 综合类 2:常见问题 / 有哪些种类
- 跳过:PRD 列了"注意事项",但 decision.suffix id=499 已有,跨 type 不重复 INSERT

### 4. qa.industry 不动
- V90 已 5 条全部 enabled=0,符合 PRD 4.1.2(qa 类型 UI 不渲染 area+industry 列)

### 5. 影响
- qa 类型从"几乎清空"重建到 24 条 enabled 词,真正承载"问答意图"
  - 进口冰箱怎么用 / 国产空调坏了怎么办 / 高品质门窗怎么保养 / 新款手机售后如何
- transaction.prefix +1 条性价比类(id=23)
- 跨 type 共存词扩大到 4 个(进口/国产/新款/最新款),符合"语义角色多元"原则

### 6. 执行后验证
- qa: prefix=5, suffix=19, industry=0
- transaction.prefix +1(id=23)
- qa.suffix sub_category 6 类:使用类 4 / 维护类 4 / 故障类 4 / 综合类 2 / 攻略类 2 / 注意事项类 3

## 2026-04-28 批 6:function 功能词类型综合审计

### 1. 整体评估
- function 类型 57 词全部 enabled,所有词已具备 sub_category + industry_tag/visual_tag
- 是审计中结构最清晰的类型,几乎不需调整
- 6 个行业 industry_tag 各 4 词(door_window/appliance/building_material/fmcg/industrial/clothing)+ common 18 词,符合 PRD 3.6.4 设计

### 2. 一处微调
- id=212"企业版" 从 function.industry 迁到 function.prefix(产品分级类,industry_tag=common)
- 理由:"企业版"是产品分级,与"基础版/旗舰/企业级/进阶版"同列,放在 industry 与"品牌/厂家/型号"主体词混类不一致
- 注意:function.prefix 已有"企业级"(id=477),与"企业版"语义略有差异(B 端定位 vs 软件版本),保留并存合理

### 3. 不动的词
- id=475"旗舰" 保持不带"版"字
  - 理由:"旗舰手机/旗舰款"在中文语境自然,改"旗舰版"反而绕
- id=175"版本"、id=176"配置" 保留在 function.industry
  - 理由:SaaS/数码场景下作主体词成立(软件版本推荐/电脑配置推荐)
- 不补 3C 数码 / 家居 / 汽车等行业的 industry_tag
  - 理由:PRD 3.6.4 期望"7 个示例行业",当前已实现 7 个;客户行业可由维护页手动添加

### 4. 影响
- function.prefix +1 -> 43 词
- function.industry -1 -> 7 词
- function.suffix 不变 -> 7 词
- 所有 function.prefix 都有 industry_tag,所有 function.industry/suffix 都无 industry_tag,字段使用一致

### 5. 执行后验证
- function: prefix=43, industry=7, suffix=7
- id=212 迁移后字段:affix_kind=prefix, sub_category=产品分级类, industry_tag=common, visual_tag=NULL
- function.prefix industry_tag=common 共 19 词
- 字段一致性检查全部通过

## 2026-04-28 批 7:历史类型(location/industry/competitor)收尾

### 1. 决策:历史类型词库全部保留,enabled 不动

经审计评估,3 个历史类型在新 6 类型设计下的可替代性各不相同:

#### location(地域词,52 词)
- 决策:全部保留 enabled=1
- 理由:area 列承载具体地名(北京/上海),与 location 类型的"本地/附近/周边"等相对地理概念是补充关系不是替代关系
- "本地XX哪家好" ≠ "北京XX哪家好",前者表达"就近"意图无法用具体地名替代
- location 类型作为整体被 V90 标记为"历史",新建场景不展示,但词库保留以支持老组继续生成

#### industry(行业词,63 词)
- 决策:全部保留 enabled=1
- 理由:industry 类型表达"宏观行业研究"搜索意图(行业现状/行业趋势/赛道分析),是真实的 B 端搜索场景(投资人/咨询师/分析师)
- PRD 6 类型设计在该意图上有空缺,可在后续阶段考虑扩展为第 7 类型
- 历史组继续可执行依赖词库保留

#### competitor(竞品词)
- 决策:已 enabled 的 31 条全部保留,已 disabled 的 21 条不变
- competitor.prefix 14 条已在历史动作中全部下架(占位符 XX 问题)
- competitor.industry 20 条 enabled、competitor.suffix 11 条 enabled 全部保留
- 理由:"新建场景不展示历史类型"是通过 KeywordTypeConfig 的类型选择器过滤实现的,不依赖 enabled 字段
- 业务能力上 competitor 大部分能被 comparison 替代,但老组继续可执行依赖词库保留

### 2. 不做的事
- 不补 sub_category 字段:sub_category 主要服务于新 6 类型的 UI 分组展示,历史类型不在新 UI 渲染,补字段无价值
- 不动 sort_order
- 不做语义优化(因为新建场景看不到这些词,优化无收益)

### 3. 影响
- 数据库无变更
- 历史类型 146 条 enabled 词全部保留:location 52 + industry 63 + competitor 31

### 4. 后续提醒
- 阶段二/三若决定彻底废弃历史类型,可在数据迁移阶段把对应组的 type 升级到新类型,然后批量下架历史词
- 当前阶段不做激进清理,降低对老客户/老数据的影响

## 2026-04-28 批 9:批 8 自然度评估失败的词库修复

### 触发原因
批 8 自然度评估整体合格率 61.8%,未通过 PRD 7.2(85%)。详见 `docs/naturalness-report-batch8.md`。

### 根因
词库与"品牌名核心词"上下文的语义错配,集中在 5 个具体词点。

### 修复动作

#### 1. brand.suffix 下架 5 条
- id=329 "品牌有哪些"
- id=346 "老牌子有哪些"
- id=773 "是什么"
- id=777 "有名吗"
- id=785 "是什么牌子"

理由:这些后缀假设核心词是品类(手机/汽车),但用户输入具体品牌名(华为/海底捞)时,"华为是什么"、"小米老牌子有哪些"语义错位。
保留 brand.suffix 12 条:怎么样/介绍/简介/好不好/口碑如何/靠不靠谱/质量怎么样/评价/值不值得买/是哪里的/是哪个公司的/哪里生产的——这些对品牌名核心词都成立。

#### 2. transaction.industry 下架 1 条
- id=196 "商品"

理由:中文"商品"暗指实物,与 SaaS 品牌(钉钉/飞书/金蝶/用友)搭配后不自然("钉钉商品"措辞不准)。
transaction.industry 剩 21 条全用"产品/服务/方案/订阅/许可证..."等 SaaS+实物通用主体。

#### 3. qa.prefix 下架 4 条
- id=816 "进口"
- id=817 "国产"
- id=818 "新款"
- id=819 "最新款"

理由:这些前缀适合品类核心词(进口手机怎么用),与具体品牌名搭配后语义错位("国产小米"冗余、"进口金蝶"不合理)。
保留 qa.prefix 1 条"高品质"(id 不在此列表)——对品牌名也成立("高品质小米使用方法")。

注:批 5 时按 PRD 3.5.4 INSERT 这 5 个词,但实测验证下与 brand 上下文(用户多用品牌名核心词)冲突。下架后 qa.prefix 词库变薄,后续可考虑加入更适合品牌名核心词的前缀(如"老款/高端/旗舰")。

#### 4. comparison.compare 下架 1 条
- id=720 "比较"

理由:中文"A 比较 B"作连接词不自然("苹果比较华为哪个更好"读起来生硬),正常说"A 对比 B"。
comparison.compare 剩 5 条:和/对比/与/VS/相比——全部自然。

#### 5. brand.prefix 下架 1 条
- id=295 "排名前十的"

理由:与具体品牌名搭配时逻辑不通("排名前十的华为"——华为是单一品牌)。
"排名前十"语义更适合 decision.suffix("XX 排名前十的品牌"),但 brand.prefix 不应有此词。

### 影响

- 全表 enabled 词减少 12 条
- brand.suffix: 17 → 12, brand.prefix: 26 → 25
- transaction.industry: 22 → 21
- qa.prefix: 5 → 1
- comparison.compare: 6 → 5

### 后续

执行批 9 后,改进抽样脚本(50% 品类核心词 + 50% 品牌核心词混合),重跑批 8 评估,目标整体合格率 ≥85%。

## 2026-04-28 批 10:批 9 评估失败的最后一轮微调

### 触发原因
批 9 二次评估整体合格率 73.5%,仍未通过 PRD 7.2(85%)。详见 `docs/naturalness-report-batch9.md`。

### 根因
- 根因 7:brand.prefix"头部"与具体品牌名核心词冲突(3 条)
- 根因 6 部分:decision.prefix"值得选的"与"怎么选/如何选择"重叠(5 条)
- 根因 6 部分:comparison.suffix"对比分析"与 compare 连接词("对比/相比")重复

### 修复动作

#### 1. brand.prefix 下架 "头部"(id=298)
- 理由:同批 9 下架"排名前十的"——"头部"形容品类前几名,与具体品牌名搭配逻辑不通("头部小米"、"头部蓝凌")
- 保留 brand.prefix 24 条:口碑好的/知名的/排名前十的(批9删)/十大(批1删)/小众但好用的(批1删)/领先的/大品牌/老牌/新锐/高端/性价比高的/国产/进口/本土/全国连锁/上市/500强/一线/二线/央企/国企/民营/龙头/靠谱的/专业的/评价高的/优秀的

#### 2. decision.prefix 下架 "值得选的"(id=463)
- 理由:与"怎么选/如何选择/选购攻略"等后缀组合后语义重叠("值得选的苹果方案怎么选" — "值得选的"+"怎么选"重复)
- 保留同类前缀:适合的/合适的/理想的/首选的/推荐的(它们和"避坑指南/选购攻略/选择指南"组合更自然)

#### 3. comparison.suffix 下架 "对比分析"(id=603)
- 理由:"对比分析"语义最泛,与 compare 连接词"对比/相比"组合时双重"对比"("小米对比华为对比分析" — 重复 2 次)
- 保留 comparison.suffix 含"对比"的其他 11 条:性能对比/价格对比/功能对比/兼容性对比/体验对比/优缺点对比/全方位对比/横向对比/深度对比/同类产品对比/实测对比
  - 它们带具体维度("性能/价格/功能"等),即使与"对比"连接词组合也不算冗余,因为表达了对比的维度细化
- 阶段二可考虑加生成器层 sub_category 互斥规则:compare='对比/相比' 时跳过 suffix 含'对比'

### 影响
- 全表 enabled 词减少 3 条
- brand.prefix: 25 → 24
- decision.prefix: 29 → 28
- comparison.suffix: 27 → 26

### 后续
执行批 10 后 + 改进抽样脚本(function 按 industry_tag 限定核心词,qa 限定实物核心词),重跑评估,目标合格率 ≥85%。
