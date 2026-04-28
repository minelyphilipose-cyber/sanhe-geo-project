# 拓词管理 V1.5 关键词自然度评估方法论

> 用途:本文档定义阶段一审计完成后(批 8)进行的"自然度抽样验收"的具体方法。
> 对应 PRD 7.2 验收门槛"自然度合格率 ≥ 85%"。
>
> 编制日期:2026-04-28
> 适用阶段:阶段一开发完成后、上线前

## 1. 评估目标

验证经过批 0-7 词库审计 + 阶段一开发后,**6 个新类型生成的关键词在中文语境下读起来是否自然**。

**输出**:
- 每个 type 的自然度合格率
- 整体合格率
- 不合格样例分析(按问题类型分类)
- 是否通过 PRD 7.2 验收的二元结论

---

## 2. 抽样设计

### 2.1 抽样规模

**目标**:每个 type 抽 17 条,6 个 type 共 102 条,接近 PRD 7.2 隐含的"100 条"规模。

理由:
- 每 type 17 条对应自然度合格率的统计置信度——按二项分布,17 条样本下,真实合格率 85% 时观测合格率 ≥ 85%(即 ≥ 15 条合格)的概率为 78%,可接受
- 抽样总量过大(如 1000 条)会让人工评估疲劳,降低判断质量

### 2.2 抽样方法

**两层抽样**:

第一层:每 type 准备 5 个测试用例,每用例生成 ~5-10 条关键词。
- 第 1 用例:1 核心词 + 5 个 prefix + 3 个 industry + 5 个 suffix(普通类型)
- 第 2 用例:2 核心词 + 5 个 prefix(prefix 多核心交叉)
- 第 3 用例:1 核心词 + 5 个 area + 3 个 industry + 5 个 suffix(地区开启,适用 decision/transaction/function)
- 第 4 用例:边界用例(如只填核心词不填其他列)
- 第 5 用例:行业切换(仅 function 类型适用)

每用例调用 `POST /api/keyword-groups/preview` 接口,从生成结果中**等概率随机抽 4 条**(总计 5 × 4 = 20 条/类型,从 20 中再筛 17 条得到目标样本量,允许小幅冗余)。

对比类型(comparison)的抽样规则:
- 第 1-3 用例:不同 coreA/coreB 组合
- 第 4 用例:边界(只 1 个 coreA + 1 个 coreB + 1 个对比词 + 1 个 suffix)
- 第 5 用例:多 compareWord(和/对比/VS 不同连接词)

### 2.3 测试数据

**核心词使用真实业务词库的代表样本**:
- toC 类:小米 / 华为 / 苹果 / 海底捞 / 星巴克
- toB 类:钉钉 / 飞书 / 用友 / 金蝶 / 蓝凌
- 行业类(用于 function 测试):双开门冰箱 / 防盗门 / 全屋净水 / 西装 / 数控机床

每 type 在 5 个用例中**用满至少 3 个不同核心词**,避免单核心词带来的偏置。

---

## 3. 自然度评分标准

### 3.1 单条关键词的二元评分

每条生成的关键词由评估员(AI 训练师)给出 0/1 评分:

**1 分(合格)**:
- 中文语法自然
- 语义清晰,意图明确(用户能理解搜索目的)
- 没有重复用词或明显冗余
- 符合 type 的搜索意图(brand 表"了解品牌"、decision 表"决策选择"等)

**0 分(不合格)**:
- 语法不通(例:"想找个靠谱的小米手机怎么样")
- 词汇重复或冗余(例:"小米品牌品牌怎么样")
- 包含未替换的占位符(XX、{}等)
- 出现广告法绝对化用语(违反 §4.5)
- 与所属 type 意图严重错位(例:brand 类型生成"小米手机多少钱")

### 3.2 评分边界规则

模糊情况一律按"严格判 0"原则:
- "这条勉强能用" → 0 分
- "这条听起来怪怪的但说得通" → 0 分
- "这条完全自然,我自己都会这么搜" → 1 分

理由:阶段一审计已经在词库层面把握了大方向,批 8 验收是"严控线",宁可低估也不高估。

### 3.3 不合格分类

不合格关键词必须打标签,标签集:

| 标签 | 含义 | 处理思路 |
|---|---|---|
| `GRAMMAR` | 语法不通顺 | 词库层面下架问题词 |
| `REDUNDANT` | 词汇重复或冗余 | 词库层面下架重复词;或调整生成器避免同类拼接 |
| `INTENT_MISMATCH` | type 意图错位 | 词库层面把词迁到正确 type |
| `UNNATURAL_LENGTH` | 词组过长读起来卡 | 调整词库,移除超长词 |
| `WEIRD_COMBO` | 单独的词都对,但拼起来怪 | 多见于"前缀 + 行业 + 后缀"三元组合,需具体分析 |
| `PLACEHOLDER` | 含未替换占位符 | 严重 bug,词库或生成器层面立即修复 |
| `LEGAL_RISK` | 含广告法风险词 | 立即下架并通报合规 |

---

## 4. 评估流程

### 4.1 准备阶段

由 codex 协助:
- 确认 #5 / #6 前端组件 + #4 options 接口已可用
- 确认 #2 后端 preview 接口已可用且生成器逻辑稳定
- 准备评估用核心词清单(见 §2.3)
- 提供测试账号(具备 keyword_group.read + 适当权限)

### 4.2 抽样阶段

由 AI 训练师执行:
1. 按 §2 抽样设计,每 type 跑 5 个测试用例
2. 调用 preview 接口拿到关键词生成结果
3. 用 Python/SQL 等概率随机抽 17 条/type,共 102 条
4. 抽样结果存入 `naturalness-sample-batch8.csv`(含 type / 核心词 / 生成关键词 / 用例 ID)

### 4.3 评分阶段

由 AI 训练师执行:
1. 逐条评分(1 / 0),不合格的打不合格分类标签
2. 评分时**先盲评不看 type**(只看关键词文本),最后再回填 type 看是否意图错位
3. 评分结果存入 `naturalness-score-batch8.csv`(含 sample_id / score / mismatch_tag / note)

### 4.4 报告阶段

由 AI 训练师生成 `naturalness-report-batch8.md`,内容包括:

1. **总体合格率**:整体 / 6 个 type 各自的合格率
2. **不合格样例 top 10**(按各分类 top 2)
3. **是否通过 PRD 7.2 验收(85%)的二元结论**
4. **如未通过的修复建议**:词库层(下架/迁入哪些词)+ 代码层(生成器逻辑)+ 配置层(KeywordTypeConfig)

---

## 5. 验收标准

### 5.1 通过标准

**整体合格率 ≥ 85%** 通过 PRD 7.2 验收。

但**任何一个 type 合格率 < 70%** 即使整体过线也判定为**条件通过**——需要补针对该 type 的修复后再做小批量复测(从该 type 重抽 10 条,复测合格率 ≥ 85% 才解除条件)。

### 5.2 不通过的处理

整体合格率 < 85% 必须修复后重测。修复策略按问题类型决定:

| 主要问题类型 | 处理 |
|---|---|
| 大部分 GRAMMAR / REDUNDANT | 词库返工,补一个临时审计批次 |
| 大部分 INTENT_MISMATCH | 检查类型配置(KeywordTypeConfig)和词库的 type 归属 |
| 大部分 UNNATURAL_LENGTH | 词库下架长词,或在生成器加长度上限 |
| 大部分 WEIRD_COMBO | 排查生成器拼接逻辑(如是否做了 sub_category 同段拼接的限制) |
| 出现 PLACEHOLDER | 立即停止验收,生成器层面 bug 修复 |
| 出现 LEGAL_RISK | 立即停止验收,通报合规 |

### 5.3 边界情况

**重测频率上限**:同一阶段最多复测 2 次。第 3 次仍不达标需回退阶段一,词库 + 类型配置 + 生成器全面 review。

**评估员单一性问题**:阶段一只有 AI 训练师一个评估员,有判断主观性。如果可以,**建议邀请 1-2 位实际客户(销售友好的真实用户)做并行评估**,合格率取两人平均。如果不可,则在报告中明确说明"单评估员"的局限。

---

## 6. 报告模板

```markdown
# 拓词 V1.5 阶段一关键词自然度评估报告

## 评估元数据

- 评估日期:YYYY-MM-DD
- 评估员:AI 训练师 / 真实客户
- 抽样总数:102 条
- 数据库基线:keyword-affix-word-current-export.csv (YYYY-MM-DD)
- 后端版本:批 N 已合并

## 总体结果

- **整体合格率**:NN/102 = NN%
- **PRD 7.2 验收(85%)**:✅ 通过 / ❌ 未通过 / ⚠️ 条件通过

## 6 个类型分项

| type | 抽样 | 合格 | 合格率 | 状态 |
|---|---|---|---|---|
| brand | 17 | NN | NN% | ✅/⚠️/❌ |
| decision | 17 | NN | NN% | ✅/⚠️/❌ |
| transaction | 17 | NN | NN% | ✅/⚠️/❌ |
| comparison | 17 | NN | NN% | ✅/⚠️/❌ |
| qa | 17 | NN | NN% | ✅/⚠️/❌ |
| function | 17 | NN | NN% | ✅/⚠️/❌ |

## 不合格分类分布

| 分类 | 数量 | 占不合格比例 |
|---|---|---|
| GRAMMAR | N | NN% |
| REDUNDANT | N | NN% |
| INTENT_MISMATCH | N | NN% |
| UNNATURAL_LENGTH | N | NN% |
| WEIRD_COMBO | N | NN% |

## 典型不合格样例(每分类 top 2)

### GRAMMAR
1. type=`xxx`,核心词=`yyy`,关键词=`...`
   - 问题:具体说明
   - 建议:具体修复

(其他分类类似)

## 修复建议

### 词库层
- ...

### 代码层
- ...

### 配置层
- ...

## 验收结论

[根据 §5 标准给出明确结论]
```

---

## 7. 工具脚本(给 codex 实施时参考)

### 7.1 抽样脚本(伪代码)

```python
import requests
import random
import csv

CORE_WORDS = {
    'toC': ['小米', '华为', '苹果', '海底捞', '星巴克'],
    'toB': ['钉钉', '飞书', '用友', '金蝶', '蓝凌'],
    'industry': ['双开门冰箱', '防盗门', '全屋净水', '西装', '数控机床'],
}

TYPES = ['brand', 'decision', 'transaction', 'comparison', 'qa', 'function']

def sample_one_type(t):
    samples = []
    for case_id in range(5):
        # 调用 preview 接口构造不同测试用例
        payload = build_payload(t, case_id)
        r = requests.post('http://localhost:8080/api/keyword-groups/preview', json=payload)
        keywords = r.json()['data']['keywords']
        # 随机抽 4 条
        chosen = random.sample(keywords, min(4, len(keywords)))
        for k in chosen:
            samples.append({
                'type': t, 'case_id': case_id,
                'core_word': payload.get('coreWords', ['']),
                'keyword': k,
            })
    # 17 条上限,多余的丢弃
    return random.sample(samples, min(17, len(samples)))

all_samples = []
for t in TYPES:
    all_samples.extend(sample_one_type(t))

with open('naturalness-sample-batch8.csv', 'w', newline='', encoding='utf-8') as f:
    writer = csv.DictWriter(f, fieldnames=['sample_id', 'type', 'case_id', 'core_word', 'keyword'])
    writer.writeheader()
    for i, s in enumerate(all_samples):
        s['sample_id'] = i + 1
        writer.writerow(s)
```

### 7.2 评分模板 CSV

`naturalness-score-batch8-template.csv`:

```csv
sample_id,type,keyword,score,mismatch_tag,note
1,brand,口碑好的小米怎么样,1,,
2,brand,XX的小米,0,PLACEHOLDER,占位符未替换
...
```

AI 训练师手填 `score` / `mismatch_tag` / `note` 三列,前 3 列从抽样脚本输出复制。

### 7.3 自动化评分汇总脚本(伪代码)

```python
import csv
from collections import defaultdict

stats = defaultdict(lambda: {'total': 0, 'pass': 0, 'fail_tags': defaultdict(int)})
with open('naturalness-score-batch8.csv', encoding='utf-8') as f:
    for row in csv.DictReader(f):
        t = row['type']
        stats[t]['total'] += 1
        if row['score'] == '1':
            stats[t]['pass'] += 1
        else:
            stats[t]['fail_tags'][row['mismatch_tag']] += 1

for t, s in stats.items():
    rate = s['pass'] / s['total'] * 100
    print(f"{t}: {s['pass']}/{s['total']} = {rate:.1f}%")
    if s['fail_tags']:
        for tag, cnt in s['fail_tags'].items():
            print(f"  {tag}: {cnt}")

total_pass = sum(s['pass'] for s in stats.values())
total = sum(s['total'] for s in stats.values())
print(f"\n整体: {total_pass}/{total} = {total_pass/total*100:.1f}%")
```

---

## 8. 后续追踪

批 8 验收通过 ≠ 永久免审计。后续阶段需要:

- **阶段二上线后 1 个月**:从生产环境抽样 200 条真实生成的关键词复评,验证生产质量
- **每次词库重大变更后**:重跑批 8 流程
- **客户反馈"生成质量差"超过 5 个时**:启动专项复评

阶段二维护页改造完成后,**追加客户自评工具**——让客户自己对生成结果给反馈(👍/👎),后端记录后批量分析,作为词库自动优化的输入。