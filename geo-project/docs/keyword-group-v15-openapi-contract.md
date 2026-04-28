# 拓词管理 V1.5 后端接口契约 OpenAPI 草案 V2.1

> 目标：字段命名先行定稿，前端 #5 可基于本文 Mock 数据开发。本文覆盖阶段一主链路，并预留阶段二手动词、审批、黑名单接口。

## 全局约定

### 分页

沿用现有分页风格：

```yaml
current:
  type: integer
  default: 1
size:
  type: integer
  default: 20
```

分页响应沿用现有 `PageResult<T>`：

```yaml
PageResult:
  type: object
  properties:
    records:
      type: array
      items: {}
    total:
      type: integer
      format: int64
    current:
      type: integer
      format: int64
    size:
      type: integer
      format: int64
```

### 空数组与 null

所有词列字段统一返回空数组 `[]`，不返回 `null`。对比词类型下不存在的 `areaWords / prefixWords / coreWords / industryWords` 也返回 `[]`；普通类型下不存在的 `coreWordsA / compareWords / coreWordsB` 也返回 `[]`。

### 业务错误码

业务错误仍包装在现有 `R` 响应内。建议 `R.message` 存用户提示，`R.code` 保持业务 HTTP 风格；新增 `errorCode` 字段可由异常处理器透出。

```yaml
BusinessError:
  type: object
  properties:
    code:
      type: integer
      example: 400
    errorCode:
      type: string
      enum:
        - BLACKLIST_HIT
        - QUOTA_EXCEEDED
        - INVALID_RESULT_KEYWORDS
        - COMPARE_CORE_A_REQUIRED
        - COMPARE_CORE_B_REQUIRED
        - COMPARE_WORD_REQUIRED
        - FUNCTION_INDUSTRY_REQUIRED
        - UNKNOWN_KEYWORD_GROUP_TYPE
    message:
      type: string
    data:
      nullable: true
```

基础参数校验继续使用 Spring `@Valid` 与现有参数异常处理，不进入上述 `errorCode` 业务错误码路径。上述错误码只覆盖需要前端按业务语义定向处理的场景。

## 核心 Schema

### KeywordWordItemRequest

```yaml
KeywordWordItemRequest:
  type: object
  required:
    - wordText
  properties:
    wordText:
      type: string
      maxLength: 64
      description: 词文本，后端 trim
    source:
      type: string
      enum: [system, custom]
      default: custom
      description: system 表示系统词库选项，custom 表示用户输入/临时词
    sortOrder:
      type: integer
      nullable: true
      description: 排序，空值时按提交顺序补齐
    isManual:
      type: boolean
      default: false
      description: 是否手动添加词
    isTemporary:
      type: boolean
      default: false
      description: 是否临时词，仅手动词有效
    scopeType:
      type: string
      nullable: true
      enum: [company, project, global]
      description: 临时词作用域；isTemporary=true 时必填
    scopeId:
      type: integer
      format: int64
      nullable: true
      description: 临时词作用域 ID；scopeType=company 时为 companyId，project 时为 projectId
```

### KeywordWordItemVO

```yaml
KeywordWordItemVO:
  allOf:
    - $ref: '#/components/schemas/KeywordWordItemRequest'
    - type: object
      properties:
        id:
          type: integer
          format: int64
          nullable: true
          description: 系统词或手动词 ID；纯自定义输入可为空
```

### KeywordGroupColumnsRequest

```yaml
KeywordGroupColumnsRequest:
  type: object
  properties:
    areaWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemRequest'
      default: []
      description: 地区词，普通结构可用
    prefixWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemRequest'
      default: []
      description: 前缀词，普通结构可用
    coreWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemRequest'
      default: []
      description: 核心词，普通结构必填
    industryWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemRequest'
      default: []
      description: 行业词，普通结构按类型决定是否必填
    suffixWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemRequest'
      default: []
      description: 后缀词；普通结构和对比结构复用
    coreWordsA:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemRequest'
      default: []
      description: 对比词专用，核心词 A
    compareWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemRequest'
      default: []
      description: 对比词专用，对比连接词
    coreWordsB:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemRequest'
      default: []
      description: 对比词专用，核心词 B
```

### KeywordGroupColumnsVO

```yaml
KeywordGroupColumnsVO:
  type: object
  properties:
    areaWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemVO'
      default: []
    prefixWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemVO'
      default: []
    coreWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemVO'
      default: []
    industryWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemVO'
      default: []
    suffixWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemVO'
      default: []
    coreWordsA:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemVO'
      default: []
    compareWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemVO'
      default: []
    coreWordsB:
      type: array
      items:
        $ref: '#/components/schemas/KeywordWordItemVO'
      default: []
```

### KeywordTypeConfig

列可见性固定使用对象，不使用数组。

字段语义说明：

- `columns.industry`：行业词列 `industryWords` 是否在 UI 上展示。
- `industryRequired`：行业词列 `industryWords` 是否必填，与列是否展示是两个概念；只有列展示时才有实际校验意义。
- `functionIndustryRequired`：功能词专属行业标签下拉框 `functionIndustryTag` 是否必填，用于决定加载哪份功能词前缀词库，不等同于 `industryWords`。
- `areaEnabledByDefault`：地区词列展示时，列内“启用地区词”开关的默认值；`columns.area=false` 表示整列隐藏，此时该默认值不参与 UI。

```yaml
KeywordTypeConfig:
  type: object
  required:
    - type
    - label
    - structure
    - columns
    - areaEnabledByDefault
    - industryRequired
    - supportsManualAdd
    - requiredColumns
  properties:
    type:
      type: string
      enum: [brand, decision, transaction, comparison, qa, function]
    label:
      type: string
    description:
      type: string
    structure:
      type: string
      enum: [standard, compare]
    areaEnabledByDefault:
      type: boolean
    industryRequired:
      type: boolean
    supportsManualAdd:
      type: boolean
    functionIndustryRequired:
      type: boolean
      default: false
    requiredColumns:
      type: object
      description: 各类型词列必填规则；functionIndustryTag 由 functionIndustryRequired 单独控制
      properties:
        area:
          type: boolean
        prefix:
          type: boolean
        core:
          type: boolean
        industry:
          type: boolean
        suffix:
          type: boolean
        compareCore:
          type: boolean
          description: 对比词核心词 A 和 B 均必填
        compareWord:
          type: boolean
    columns:
      type: object
      properties:
        area:
          type: boolean
        prefix:
          type: boolean
        core:
          type: boolean
        industry:
          type: boolean
        suffix:
          type: boolean
        compareCore:
          type: boolean
        compareWord:
          type: boolean
```

6 类型配置固定如下：

```json
[
  {
    "type": "brand",
    "label": "品牌词",
    "description": "用户带品牌名称搜索，了解品牌",
    "structure": "standard",
    "areaEnabledByDefault": false,
    "industryRequired": false,
    "supportsManualAdd": true,
    "functionIndustryRequired": false,
    "requiredColumns": {
      "area": false,
      "prefix": false,
      "core": true,
      "industry": false,
      "suffix": false,
      "compareCore": false,
      "compareWord": false
    },
    "columns": {
      "area": false,
      "prefix": true,
      "core": true,
      "industry": true,
      "suffix": true,
      "compareCore": false,
      "compareWord": false
    }
  },
  {
    "type": "decision",
    "label": "决策词",
    "description": "用户已决定购买，问选哪家",
    "structure": "standard",
    "areaEnabledByDefault": true,
    "industryRequired": true,
    "supportsManualAdd": true,
    "functionIndustryRequired": false,
    "requiredColumns": {
      "area": false,
      "prefix": false,
      "core": true,
      "industry": true,
      "suffix": false,
      "compareCore": false,
      "compareWord": false
    },
    "columns": {
      "area": true,
      "prefix": true,
      "core": true,
      "industry": true,
      "suffix": true,
      "compareCore": false,
      "compareWord": false
    }
  },
  {
    "type": "transaction",
    "label": "成交词",
    "description": "用户要下单，问价格或购买渠道",
    "structure": "standard",
    "areaEnabledByDefault": true,
    "industryRequired": false,
    "supportsManualAdd": true,
    "functionIndustryRequired": false,
    "requiredColumns": {
      "area": false,
      "prefix": false,
      "core": true,
      "industry": false,
      "suffix": false,
      "compareCore": false,
      "compareWord": false
    },
    "columns": {
      "area": true,
      "prefix": true,
      "core": true,
      "industry": true,
      "suffix": true,
      "compareCore": false,
      "compareWord": false
    }
  },
  {
    "type": "comparison",
    "label": "对比词",
    "description": "用户在多个品牌或产品之间比较",
    "structure": "compare",
    "areaEnabledByDefault": false,
    "industryRequired": false,
    "supportsManualAdd": true,
    "functionIndustryRequired": false,
    "requiredColumns": {
      "area": false,
      "prefix": false,
      "core": false,
      "industry": false,
      "suffix": true,
      "compareCore": true,
      "compareWord": true
    },
    "columns": {
      "area": false,
      "prefix": false,
      "core": false,
      "industry": false,
      "suffix": true,
      "compareCore": true,
      "compareWord": true
    }
  },
  {
    "type": "qa",
    "label": "问答词",
    "description": "用户有使用或认知问题要解答",
    "structure": "standard",
    "areaEnabledByDefault": false,
    "industryRequired": false,
    "supportsManualAdd": true,
    "functionIndustryRequired": false,
    "requiredColumns": {
      "area": false,
      "prefix": false,
      "core": true,
      "industry": false,
      "suffix": false,
      "compareCore": false,
      "compareWord": false
    },
    "columns": {
      "area": false,
      "prefix": true,
      "core": true,
      "industry": false,
      "suffix": true,
      "compareCore": false,
      "compareWord": false
    }
  },
  {
    "type": "function",
    "label": "功能词",
    "description": "用户有特定功能或品质需求",
    "structure": "standard",
    "areaEnabledByDefault": true,
    "industryRequired": false,
    "supportsManualAdd": true,
    "functionIndustryRequired": true,
    "requiredColumns": {
      "area": false,
      "prefix": false,
      "core": true,
      "industry": false,
      "suffix": false,
      "compareCore": false,
      "compareWord": false
    },
    "columns": {
      "area": true,
      "prefix": true,
      "core": true,
      "industry": true,
      "suffix": true,
      "compareCore": false,
      "compareWord": false
    }
  }
]
```

### KeywordAffixWordOptionItem

```yaml
KeywordAffixWordOptionItem:
  type: object
  properties:
    id:
      type: integer
      format: int64
    wordText:
      type: string
    subCategory:
      type: string
      nullable: true
    visualTag:
      type: string
      nullable: true
      description: toB/toC/common 等展示标签
    industryTag:
      type: string
      nullable: true
    isManual:
      type: boolean
    isTemporary:
      type: boolean
    scopeType:
      type: string
      nullable: true
      enum: [company, project, global]
    scopeId:
      type: integer
      format: int64
      nullable: true
    sortOrder:
      type: integer
```

### KeywordAffixWordOptionVO

```yaml
KeywordAffixWordOptionVO:
  type: object
  properties:
    typeConfigs:
      type: array
      items:
        $ref: '#/components/schemas/KeywordTypeConfig'
    currentTypeConfig:
      $ref: '#/components/schemas/KeywordTypeConfig'
      nullable: true
      description: 入参 type 对应的配置；type 为空或未知时为空
    areaWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordAffixWordOptionItem'
      default: []
    prefixWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordAffixWordOptionItem'
      default: []
    industryWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordAffixWordOptionItem'
      default: []
    suffixWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordAffixWordOptionItem'
      default: []
    compareWords:
      type: array
      items:
        $ref: '#/components/schemas/KeywordAffixWordOptionItem'
      default: []
```

过滤规则：options 接口必须使用 `enabled=1 AND (is_manual=0 OR approval_status='approved')`。`includeManual=true` 时返回当前 `scopeType/scopeId` 可见的临时词，并带 `isTemporary/scopeType/scopeId`，前端据此浅黄色标识。

### KeywordGroupPayloadRequest

```yaml
KeywordGroupPayloadRequest:
  type: object
  required:
    - companyId
    - type
    - columns
  properties:
    companyId:
      type: integer
      format: int64
    projectId:
      type: integer
      format: int64
      nullable: true
      description: 阶段三配额使用；为空时按基础配额兜底
    name:
      type: string
      maxLength: 64
      description: 预览时不必填；保存新增/编辑时必填
    type:
      type: string
      description: brand/decision/transaction/comparison/qa/function；历史组可能为 search/location/industry/competitor
    areaEnabled:
      type: boolean
      nullable: true
      description: 是否启用地区词；为空时按类型默认值
    functionIndustryTag:
      type: string
      nullable: true
      description: 功能词行业标签
    remark:
      type: string
      nullable: true
      maxLength: 255
    count:
      type: integer
      default: 1000
    resultKeywords:
      type: array
      items:
        type: string
      default: []
      description: 保存时必填，必须来自预览候选池
    columns:
      $ref: '#/components/schemas/KeywordGroupColumnsRequest'
```

### KeywordPreviewVO

```yaml
KeywordPreviewVO:
  type: object
  properties:
    totalEstimated:
      type: integer
      format: int64
    totalAvailable:
      type: integer
      format: int64
    totalGenerated:
      type: integer
      format: int32
    filteredCount:
      type: integer
      format: int32
      description: 被黑名单后置过滤丢弃的数量
    keywords:
      type: array
      items:
        type: string
```

### KeywordGroupVO

```yaml
KeywordGroupVO:
  type: object
  properties:
    id:
      type: integer
      format: int64
    companyId:
      type: integer
      format: int64
    companyName:
      type: string
      nullable: true
    projectId:
      type: integer
      format: int64
      nullable: true
    projectName:
      type: string
      nullable: true
    packageType:
      type: string
      nullable: true
    name:
      type: string
    type:
      type: string
    typeLabel:
      type: string
    legacyType:
      type: boolean
      description: 是否历史类型 search/location/industry/competitor
    areaEnabled:
      type: boolean
      nullable: true
    functionIndustryTag:
      type: string
      nullable: true
    remark:
      type: string
      nullable: true
    estimatedKeywordCount:
      type: integer
      format: int64
      description: 按当前 columns 理论可生成的关键词数量，受 1000 条上限前的估算影响
    savedKeywordCount:
      type: integer
      format: int64
      description: 当前拓词组已保存的关键词数量
    columns:
      $ref: '#/components/schemas/KeywordGroupColumnsVO'
    createdAt:
      type: string
      format: date-time
    updatedAt:
      type: string
      format: date-time
```

详情接口统一返回完整 columns 键，所有不存在的列返回 `[]`。

### KeywordGroupListItemVO

```yaml
KeywordGroupListItemVO:
  type: object
  properties:
    id:
      type: integer
      format: int64
    companyId:
      type: integer
      format: int64
    companyName:
      type: string
      nullable: true
    projectId:
      type: integer
      format: int64
      nullable: true
    projectName:
      type: string
      nullable: true
    packageType:
      type: string
      nullable: true
    name:
      type: string
    type:
      type: string
    typeLabel:
      type: string
    legacyType:
      type: boolean
      description: 是否历史类型 search/location/industry/competitor
    savedKeywordCount:
      type: integer
      format: int64
      description: 当前拓词组已保存的关键词数量
    updatedAt:
      type: string
      format: date-time
```

## 接口定义

### GET /api/keyword-affix-words/options

拓词页词库 options 接口。仅覆盖拓词页，不改变其他业务字典接口。

```yaml
get:
  summary: Get keyword group word options
  parameters:
    - name: type
      in: query
      schema:
        type: string
      description: brand/decision/transaction/comparison/qa/function
    - name: industryTag
      in: query
      schema:
        type: string
      description: 功能词行业标签；为空时仅返回 common 与非行业限定词
    - name: includeManual
      in: query
      schema:
        type: boolean
        default: false
    - name: scopeType
      in: query
      schema:
        type: string
        enum: [company, project, global]
    - name: scopeId
      in: query
      schema:
        type: integer
        format: int64
  responses:
    '200':
      description: OK
      content:
        application/json:
          schema:
            type: object
            properties:
              code:
                type: integer
              message:
                type: string
              data:
                $ref: '#/components/schemas/KeywordAffixWordOptionVO'
```

### POST /api/keyword-groups/preview

```yaml
post:
  summary: Preview keyword group generated keywords
  requestBody:
    required: true
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/KeywordGroupPayloadRequest'
  responses:
    '200':
      description: OK
      content:
        application/json:
          schema:
            type: object
            properties:
              code:
                type: integer
              message:
                type: string
              data:
                $ref: '#/components/schemas/KeywordPreviewVO'
    '400':
      description: Business error
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/BusinessError'
```

校验规则：

- 后端按 `KeywordTypeConfig.requiredColumns` 统一校验词列必填规则。
- `requiredColumns.core=true`：`coreWords` 必填。
- `requiredColumns.industry=true`：`industryWords` 必填。
- `requiredColumns.compareCore=true`：`coreWordsA / coreWordsB` 均必填；缺 `coreWordsA` 返回 `COMPARE_CORE_A_REQUIRED`，缺 `coreWordsB` 返回 `COMPARE_CORE_B_REQUIRED`。
- `requiredColumns.compareWord=true`：`compareWords` 必填，缺失返回 `COMPARE_WORD_REQUIRED`。
- `requiredColumns.suffix=true`：`suffixWords` 必填；缺失走 Spring `@Valid` 默认参数校验，不进入业务 `errorCode` 路径。
- `functionIndustryRequired=true`：`functionIndustryTag` 必填，缺失返回 `FUNCTION_INDUSTRY_REQUIRED`。

### POST /api/keyword-groups

```yaml
post:
  summary: Create keyword group
  requestBody:
    required: true
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/KeywordGroupPayloadRequest'
  responses:
    '200':
      description: OK
      content:
        application/json:
          schema:
            type: object
            properties:
              code:
                type: integer
              message:
                type: string
              data:
                $ref: '#/components/schemas/KeywordGroupVO'
```

保存时 `name / resultKeywords` 必填，`resultKeywords` 必须来自预览候选池，否则返回 `INVALID_RESULT_KEYWORDS`。预览接口不要求 `name`。

### PUT /api/keyword-groups/{id}

同 `POST /api/keyword-groups`，路径参数：

```yaml
parameters:
  - name: id
    in: path
    required: true
    schema:
      type: integer
      format: int64
```

保存编辑时 `name / resultKeywords` 必填，`resultKeywords` 必须来自预览候选池，否则返回 `INVALID_RESULT_KEYWORDS`。

### GET /api/keyword-groups/{id}

```yaml
get:
  summary: Get keyword group detail
  parameters:
    - name: id
      in: path
      required: true
      schema:
        type: integer
        format: int64
  responses:
    '200':
      description: OK
      content:
        application/json:
          schema:
            type: object
            properties:
              code:
                type: integer
              message:
                type: string
              data:
                $ref: '#/components/schemas/KeywordGroupVO'
```

详情统一返回完整 columns 键，不存在列为 `[]`。

### GET /api/keyword-groups

分页列表沿用现有参数，`type` 仍为单选。

```yaml
get:
  summary: Page keyword groups
  parameters:
    - name: current
      in: query
      schema:
        type: integer
        default: 1
    - name: size
      in: query
      schema:
        type: integer
        default: 20
    - name: keyword
      in: query
      schema:
        type: string
    - name: companyId
      in: query
      schema:
        type: integer
        format: int64
    - name: projectId
      in: query
      schema:
        type: integer
        format: int64
    - name: type
      in: query
      schema:
        type: string
  responses:
    '200':
      description: OK
      content:
        application/json:
          schema:
            type: object
            properties:
              code:
                type: integer
              message:
                type: string
              data:
                allOf:
                  - $ref: '#/components/schemas/PageResult'
                  - type: object
                    properties:
                      records:
                        type: array
                        items:
                          $ref: '#/components/schemas/KeywordGroupListItemVO'
```

### POST /api/keyword-affix-words/manual

权限：

- `saveMode=temporary`：需要 `keyword_affix.add_temporary`
- `saveMode=permanent` 且当前用户可直接入库：需要 `keyword_affix.manage`
- `saveMode=permanent` 且当前用户提交审批：需要 `keyword_affix.propose`

```yaml
ManualKeywordAffixWordCreateRequest:
  type: object
  required:
    - type
    - affixKind
    - words
    - saveMode
  properties:
    type:
      type: string
    affixKind:
      type: string
      enum: [area, prefix, industry, suffix, compare]
    words:
      type: array
      minItems: 1
      items:
        type: string
    saveMode:
      type: string
      enum: [temporary, permanent]
    scopeType:
      type: string
      nullable: true
      enum: [company, project, global]
      description: temporary 必填
    scopeId:
      type: integer
      format: int64
      nullable: true
      description: temporary 必填，global 可为空
    industryTag:
      type: string
      nullable: true
    visualTag:
      type: string
      nullable: true
    subCategory:
      type: string
      nullable: true
    approvalReason:
      type: string
      nullable: true
      description: permanent 且走审批时必填

ManualKeywordAffixWordCreateResult:
  type: object
  properties:
    insertedCount:
      type: integer
    items:
      type: array
      items:
        $ref: '#/components/schemas/KeywordAffixWordOptionItem'

BlacklistHit:
  type: object
  properties:
    word:
      type: string
    matchedRule:
      type: string
    matchType:
      type: string
      enum: [exact, contains]
```

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "insertedCount": 2,
    "items": []
  }
}
```

命中黑名单响应：

```json
{
  "code": 400,
  "errorCode": "BLACKLIST_HIT",
  "message": "词条命中黑名单",
  "data": {
    "hits": [
      { "word": "xxx", "matchedRule": "xxx", "matchType": "contains" }
    ]
  }
}
```

状态机说明：

- `saveMode=temporary`：写入临时词，`is_temporary=1`，默认 `approval_status='approved'`，要求 `scopeType/scopeId` 满足作用域规则。
- `saveMode=permanent` 且用户具备 `keyword_affix.manage`：直接写入正式词，`approval_status='approved'`。
- `saveMode=permanent` 且用户仅具备 `keyword_affix.propose`：写入待审词，`approval_status='pending'`，此时 `approvalReason` 必填。
- `saveMode=permanent` 且用户同时具备 `manage/propose`：以后端权限判断为准，优先按 `manage` 直接入库。

### GET /api/admin/keyword-affix-words/manual-pending

权限：`keyword_affix.approve`

```yaml
get:
  summary: Page pending manual words
  parameters:
    - name: current
      in: query
      schema:
        type: integer
        default: 1
    - name: size
      in: query
      schema:
        type: integer
        default: 20
    - name: type
      in: query
      schema:
        type: string
    - name: affixKind
      in: query
      schema:
        type: string
    - name: keyword
      in: query
      schema:
        type: string
  responses:
    '200':
      description: PageResult of KeywordAffixWordOptionItem with approval fields
```

列表项除 `KeywordAffixWordOptionItem` 外，增加：

```yaml
approvalStatus:
  type: string
  enum: [pending, approved, rejected]
approvalReason:
  type: string
  nullable: true
addedByUserId:
  type: integer
  format: int64
approvedBy:
  type: integer
  format: int64
  nullable: true
approvedAt:
  type: string
  format: date-time
  nullable: true
```

### PUT /api/admin/keyword-affix-words/{id}/approve

权限：`keyword_affix.approve`

```yaml
put:
  summary: Approve manual word
  parameters:
    - name: id
      in: path
      required: true
      schema:
        type: integer
        format: int64
  requestBody:
    required: false
    content:
      application/json:
        schema:
          type: object
          properties:
            remark:
              type: string
              nullable: true
  responses:
    '200':
      description: OK
```

实现说明：批准时需要重新执行 `BlacklistService.checkWords`。若当前词命中黑名单，审批接口拒绝批准并返回 `BLACKLIST_HIT`，词条保持 `pending`，防止提交后黑名单更新导致漏判。

单条审批接口阶段二保留；前端可循环调用实现批量批准/拒绝，失败的词单独提示。批量审批接口作为后续优化，不进入一期范围。

### PUT /api/admin/keyword-affix-words/{id}/reject

权限：`keyword_affix.approve`

```yaml
put:
  summary: Reject manual word
  parameters:
    - name: id
      in: path
      required: true
      schema:
        type: integer
        format: int64
  requestBody:
    required: true
    content:
      application/json:
        schema:
          type: object
          required:
            - rejectReason
          properties:
            rejectReason:
              type: string
              minLength: 1
  responses:
    '200':
      description: OK
```

## BlacklistService 契约

Java 服务接口：

```java
public record BlacklistHit(
        String word,
        String matchedRule,
        String matchType
) {}

public record BlacklistCheckResult(
        boolean passed,
        List<BlacklistHit> hits
) {}

public interface BlacklistService {
    BlacklistCheckResult checkWords(List<String> words);

    List<String> filterKeywords(List<String> keywords);
}
```

匹配规则：

- 标准化等于 OR 子串包含。
- `normalizedRule.length() < 2` 时只做精确等于，不做 contains。
- `filterKeywords` 命中后丢弃整条关键词，不替换片段。

## OpenAPI 组件索引

```yaml
components:
  schemas:
    BusinessError: {}
    PageResult: {}
    KeywordWordItemRequest: {}
    KeywordWordItemVO: {}
    KeywordGroupColumnsRequest: {}
    KeywordGroupColumnsVO: {}
    KeywordTypeConfig: {}
    KeywordAffixWordOptionItem: {}
    KeywordAffixWordOptionVO: {}
    KeywordGroupPayloadRequest: {}
    KeywordPreviewVO: {}
    KeywordGroupVO: {}
    KeywordGroupListItemVO: {}
    ManualKeywordAffixWordCreateRequest: {}
    ManualKeywordAffixWordCreateResult: {}
    BlacklistHit: {}
```
