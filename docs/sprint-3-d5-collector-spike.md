# Sprint 3 D5 Collector Spike

目标：给 Sprint 3 W1 直接进入实施用。本文只覆盖外部 URL/RSS 采集到 `article_draft` 的技术方案，不实现代码。

## 0. 结论摘要

- URL 单点采集先做 D5a：`用户 URL -> SSRF 校验 -> 抓取 -> jsoup 提取正文 -> article_draft_version`。
- RSS 订阅拆到 D5b：feed 订阅、定时刷新、条目去重、逐条复用 D5a URL collector。
- AI 改写 diff 拆到 D5c：保留原文和改写后内容，diff 运行时计算，不先落 diff 数据。
- 抓取/解析库选择：`Apache HttpClient 5` 做 HTTP 传输，`jsoup` 做 HTML 解析和正文提取启发式；不引入 Readability4J。
- 测试 HTTP mock 选择：`WireMock 3.x`，不使用 4.x beta。

## 1. 抓取库选型

### 1.1 候选对比

| 候选 | 维护状态 | License | 依赖体积 | 中文正文提取效果 | 结论 |
| --- | --- | --- | --- | --- | --- |
| Readability4J | `net.dankito.readability4j` Maven Central 最新 1.0.7，2021-10-26；`org.openimaj/readability4j` 最新 1.3.10，2020-02-09 | Readability4J 变体需逐包确认，维护弱 | 额外引入正文算法封装 | 对正文抽取目标贴近，但中文站点模板复杂，仍需人工兜底 | 不选 |
| jsoup | 官方 1.22.2 release date 2026-04-20；项目现有 `pom.xml` 已有 jsoup 1.17.2 | MIT | 已在项目中，无新增核心依赖 | HTML5 容错解析好；正文提取需自写启发式 | 选为 HTML parser |
| Apache HttpClient + 自己写 | HttpClient 5.5.2 Maven Central 可用，Apache 2.0；HttpComponents 维护活跃 | Apache 2.0 | 中等，传输层依赖清晰 | 不负责正文提取 | 选为 HTTP client |

信息来源：jsoup 官方 release/news 和 license 页面、Maven Central 的 Readability4J / HttpClient 5 metadata。

### 1.2 最终选择

选择：`Apache HttpClient 5 + jsoup`。

理由：

1. 项目已依赖 jsoup，D5a 只需要升级版本或复用现有版本，不引入陌生正文提取包。
2. HttpClient 5 对超时、重定向禁用、响应流控制、连接池和 header 管理更可控，适合作为 SSRF 防护边界的传输层。
3. 中文站点正文提取不能完全依赖 Readability 类算法；业务更需要可解释启发式和失败原因，便于运营补录。

已知风险：

- 自写正文启发式比 Readability 类算法维护成本高。D5a 必须把正文提取逻辑封装成 `ArticleContentExtractor`，保留后续替换为 Readability 算法的接口边界。

### 1.3 未选择说明

- 不选 Readability4J：维护状态弱，Maven 最新版本停在 2021 或 2020，且中文站点仍需兜底逻辑，收益不足以抵消依赖风险。
- 不选裸 `Apache HttpClient + 自己写 HTML parser`：HTML 容错解析不应手写，jsoup 已解决“真实世界 HTML”问题。
- 不只用 jsoup 自带 `connect()`：SSRF 防护需要重定向逐跳校验、响应大小流控、DNS/IP 校验和清晰 timeout；HttpClient 5 更适合做安全边界。

## 2. SSRF 防护具体实现

### 2.1 URL scheme

决策：D5a 允许 `https` 和 `http`，但默认推荐运营使用 `https`。

理由：

- 国内大量资讯站、地方站、老 CMS 仍有 `http` 内容页。完全禁用会降低采集成功率。
- SSRF 风险不主要来自 http/https，而来自目标 IP、重定向链、协议绕过和响应大小。

实现策略：

- `URI uri = URI.create(input.trim())` 后 normalize。
- scheme 小写后只允许 `http` / `https`。
- 必须有 host，拒绝 userinfo：`http://user:pass@example.com`。
- 拒绝 fragment 参与 fetch；可保留原始 URL 做溯源。
- 拒绝非默认端口以外的内网敏感端口不够，不能靠端口做安全；IP 校验才是主边界。端口允许 80/443/站点自定义端口，但每跳都必须通过 IP 校验。

错误码：

- scheme/host/userinfo 不合法：`80300 URL_INVALID`。

### 2.2 DNS 解析后 IP 校验

必须 reject：

- IPv4 私网：`10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`
- loopback：`127.0.0.0/8`
- link-local：`169.254.0.0/16`
- multicast/reserved/unspecified：`0.0.0.0/8`、`224.0.0.0/4`、`240.0.0.0/4`
- IPv6 loopback：`::1/128`
- IPv6 unspecified：`::/128`
- IPv6 unique local：`fc00::/7`
- IPv6 link-local：`fe80::/10`
- IPv4-mapped IPv6：如 `::ffff:127.0.0.1`，先转出 IPv4 再按 IPv4 规则校验

推荐 lib：

- JDK `InetAddress` + Guava `InetAddresses`。如果不想引入 Guava，使用 JDK 判断加自写 CIDR matcher。
- 推荐不新增 Guava，仅实现一个 `IpRangeBlocklist`，因为项目当前未见 Guava 依赖。

实现策略：

```java
List<InetAddress> addresses = dnsResolver.resolve(host);
if (addresses.isEmpty()) throw URL_INVALID;
for (InetAddress address : addresses) {
    if (ipBlocklist.isBlocked(address)) throw URL_BLOCKED_PRIVATE_IP;
}
```

关键点：

- 一个 host 解析出多个 IP，只要任一 IP 是 blocked，就拒绝整个 URL。
- 不允许“过滤掉坏 IP 后用好 IP 继续”，否则 DNS rebinding/轮询场景容易误判。
- `DnsResolver` 做成接口，生产用 JDK DNS，测试可注入返回私网/公网/变化 IP。

错误码：

- 命中私网/保留地址：`80301 URL_BLOCKED_PRIVATE_IP`。

### 2.3 重定向链每跳校验

决策：禁用 HttpClient 自动重定向，应用层手动处理。

实现策略：

- HttpClient 配置 `disableRedirectHandling()`。
- 收到 `301/302/303/307/308`：
  - 读取 `Location`。
  - 用当前 URI resolve 相对地址。
  - 对新 URI 重新跑 scheme/host/DNS/IP 校验。
  - 最多 5 跳。
  - 每跳写入 `fetch_redirect_chain` 内存对象，audit 只记录跳数和最终 host，不记录完整 query。

错误码：

- Location 非法：`80300 URL_INVALID`
- 跳到私网：`80301 URL_BLOCKED_PRIVATE_IP`
- 超过跳数：`80307 REDIRECT_TOO_DEEP`

### 2.4 响应大小上限

决策：HTML 响应上限 5MB。

实现策略：

- 先看 `Content-Length`，大于 5MB 直接拒绝。
- 对 chunked/无 Content-Length 响应，用 `BoundedInputStream` 或自写计数 wrapper，超过 5MB 立即 abort connection。
- 不把完整 body 读入无限 buffer。
- 仅接受 `text/html`、`application/xhtml+xml`、`text/plain`。RSS 场景另允许 XML/feed 类型。

推荐 lib：

- Apache Commons IO `BoundedInputStream` 如果项目已有 commons-io；否则自写 `LimitedInputStream`，代码量很小。

错误码：

- 超过大小：`80303 FETCH_TOO_LARGE`。

### 2.5 响应超时

决策：

- connect timeout：5s
- response/read timeout：30s
- connection request timeout：2s

实现策略：

- HttpClient 5 `RequestConfig`：
  - `setConnectTimeout(Timeout.ofSeconds(5))`
  - `setResponseTimeout(Timeout.ofSeconds(30))`
  - `setConnectionRequestTimeout(Timeout.ofSeconds(2))`

错误码：

- connect/read timeout 统一：`80302 FETCH_TIMEOUT`。

### 2.6 User-Agent

决策：固定可识别 UA，不伪装浏览器。

建议：

```text
SanheGeoCollector/1.0 (+https://sanhe.example.com/collector; contact=ops@sanhe.example.com)
```

实现策略：

- 配置项 `geo.collector.user-agent`，默认如上。
- 每次 fetch 带 `Accept: text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.1`。
- RSS feed fetch 带 `Accept: application/rss+xml,application/atom+xml,application/xml,text/xml;q=0.9,*/*;q=0.1`。

## 3. RSS 采集与 URL 采集关系

### 3.1 范围拆分

| PR | 范围 | 不包含 |
| --- | --- | --- |
| D5a | URL 单点采集、SSRF 防护、HTML fetch、正文提取、落库 article draft | RSS feed、调度、AI 改写 |
| D5b | RSS feed 订阅、定时刷新、条目去重、逐条调用 D5a collector | AI 改写 diff |
| D5c | 原文保留、AI 改写、运行时 diff 展示/接口 | feed 调度 |

原因：

- D5a 是安全边界，必须单独 review。
- D5b 是调度和幂等问题，风险面不同。
- D5c 涉及法务溯源和 AI 出站策略，不应塞进抓取 PR。

### 3.2 D5a URL 单点流程

```text
POST /api/content/articles/collect-url
  -> BrandAccess OPERATE
  -> UrlNormalizer.normalize()
  -> UrlSafetyGuard.validateInitial()
  -> CollectorHttpClient.fetchWithManualRedirects()
  -> ArticleContentExtractor.extract()
  -> duplicate check by canonical_url_hash
  -> ContentArticleService create draft, status=pending_review
```

响应建议：

```json
{
  "articleId": 123,
  "status": "pending_review",
  "sourceUrl": "https://example.com/a",
  "canonicalUrl": "https://example.com/a",
  "duplicate": false
}
```

### 3.3 D5b RSS feed 流程

推荐 RSS parser：ROME (`com.rometools:rome`)。

理由：

- ROME 官方定位为 Java RSS/Atom framework，Apache 2.0。
- 支持 RSS 和 Atom，不需要自己处理 feed dialect。
- D5b 只用 parser，不把网络抓取交给 ROME；fetch 仍走 D5a 安全 HTTP client。

feed 频率：

- 固定 cron：每 30 分钟扫描 active feed。
- 如果 feed 自报 `ttl`，取 `max(ttl, 30min)`，即只允许降低频率，不允许高于系统下限。
- 每个 feed 记录 `next_fetch_at`，job 只拉到期 feed。

不选择“完全按 feed ttl”的理由：

- 站点 ttl 不可信，可能过小导致抓取压力和被封风险。
- 固定下限便于容量估算。

### 3.4 去重策略

决策：URL hash 唯一索引为主，标题相似度只做人工提示，不做硬去重。

字段建议：

- `source_url`：用户输入或 feed item link。
- `canonical_url`：归一化后 URL。
- `canonical_url_hash`：SHA-256 hex。
- `source_kind`：`URL` / `RSS`.
- 唯一索引：`uk_article_source_url_hash(brand_id, canonical_url_hash)`。

URL 归一化：

- host 小写。
- 默认端口去掉。
- fragment 去掉。
- query 保留，但删除常见 tracking 参数：`utm_*`、`spm`、`from`、`source`、`fbclid`、`gclid`。
- path 不强制去尾 slash，避免站点语义差异。

为什么不选标题相似度硬去重：

- 中文标题改写/转载经常相似，硬去重误伤高。
- 标题相似度可作为“疑似重复”提示，后续后台 UI 再处理。

## 4. 改写 Diff 算法

### 4.1 候选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 完整保留原文 + 改写后内容，diff 运行时计算 | 法务溯源完整；落库简单；算法可替换 | 存储占用更高；展示时多一步计算 | 选 |
| 落库时存 diff-match-patch 格式 | 展示快；版本固定 | 算法变更困难；diff 数据和正文可能不一致 | 不选 |
| 只存原文 hash | 存储少 | 法务无法复核“改了什么” | 不选 |

### 4.2 字段设计建议

新增或扩展 `article_draft_version`：

- `source_kind varchar(20)`：`MANUAL` / `AI` / `URL` / `RSS`
- `source_url varchar(1024)`
- `canonical_url varchar(1024)`
- `canonical_url_hash char(64)`
- `source_title varchar(512)`
- `source_author varchar(255)`
- `source_published_at datetime null`
- `source_fetched_at datetime not null`
- `source_content_markdown mediumtext`
- `content_markdown mediumtext`：当前系统已有，存最终草稿/改写后内容
- `rewrite_model_platform_code varchar(64) null`
- `rewrite_model_id varchar(128) null`

Diff 展示：

- 后端接口按需计算：`GET /api/content/articles/{id}/rewrite-diff`
- 算法：Google `diff-match-patch` Java port 或 java-diff-utils。
- D5c 推荐 `java-diff-utils`，Apache 2.0，按行级/词级 diff 更适合 markdown 审阅。

合规策略：

- DB 保留原文用于法务溯源。
- audit detail 不记录原文全文，只记录 `source_url_hash`、长度、提取状态、错误码。

## 5. 错误码与 Audit

### 5.1 错误码 80300-80399

| code | name | HTTP | 归类 | 含义 |
| --- | --- | --- | --- | --- |
| 80300 | URL_INVALID | 400 | DENIED | URL 格式、scheme、host、userinfo、redirect location 非法 |
| 80301 | URL_BLOCKED_PRIVATE_IP | 403 | DENIED | 初始 URL 或重定向目标解析到私网/保留/IPV6 blocked 段 |
| 80302 | FETCH_TIMEOUT | 504 | FAILURE | connect/read timeout |
| 80303 | FETCH_TOO_LARGE | 413 | DENIED | Content-Length 或读取流超过 5MB |
| 80304 | EXTRACT_FAILED | 422 | FAILURE | HTML 成功获取但无法提取有效标题/正文 |
| 80305 | RSS_PARSE_FAILED | 422 | FAILURE | feed XML 解析失败 |
| 80306 | DUPLICATE_URL | 409 | DENIED | `brand_id + canonical_url_hash` 已存在 |
| 80307 | REDIRECT_TOO_DEEP | 400 | DENIED | 重定向超过 5 跳 |
| 80308 | UNSUPPORTED_CONTENT_TYPE | 415 | DENIED | 响应 Content-Type 不在允许集合 |
| 80309 | FETCH_FAILED | 502 | FAILURE | 非 timeout 的网络错误、DNS 失败、HTTP 5xx |
| 80310 | RSS_FEED_NOT_MODIFIED | 304/200 | SUCCESS | feed 未更新；业务上不是异常，可不抛 BizException |
| 80311 | RSS_ITEM_URL_MISSING | 422 | FAILURE | feed item 没有可用 link/guid URL |

同步要求：

- `80300/80301/80303/80306/80307/80308` 进 `AuditOperationAspect.DENIED_CODES`。
- `80302/80304/80305/80309/80311` 保持 FAILURE 默认。
- `80310` 不进错误集合，建议不作为异常码使用，仅作为 audit detail status。

### 5.2 Audit event_type

| event_type | result | sensitive | target | detail 字段 |
| --- | --- | --- | --- | --- |
| URL_COLLECTOR_FETCH_REQUESTED | SUCCESS/DENIED | false | brand/project/article | `sourceHost`, `scheme`, `urlHash` |
| URL_COLLECTOR_FETCHED | SUCCESS/FAILURE/DENIED | false | brand/project/article | `statusCode`, `contentType`, `bytesRead`, `redirectCount`, `durationMs`, `urlHash` |
| URL_COLLECTOR_EXTRACTED | SUCCESS/FAILURE | false | article | `titleLength`, `contentLength`, `extractorVersion` |
| URL_COLLECTOR_DUPLICATE_DENIED | DENIED | false | brand/article | `urlHash`, `existingArticleId` |
| RSS_FEED_SUBSCRIBED | SUCCESS/DENIED | false | brand/feed | `feedUrlHash`, `sourceHost` |
| RSS_FEED_REFRESHED | SUCCESS/FAILURE | false | feed | `itemCount`, `newItemCount`, `duplicateCount`, `durationMs` |
| RSS_ITEM_COLLECTED | SUCCESS/FAILURE/DENIED | false | feed/article | `itemUrlHash`, `status` |
| ARTICLE_REWRITE_DIFF_VIEWED | SUCCESS | false | article | `sourceVersionId`, `draftVersionId` |

禁止：

- audit detail 不记录完整 URL query。
- audit detail 不记录原文正文、AI prompt、改写全文。
- fetch 失败日志不打印响应 body。

## 6. 测试基础设施

### 6.1 HTTP mock 选型

选择：WireMock 3.x。

理由：

1. HTTP 场景表达力强：重定向、header、delay、chunked/body、不同 Content-Type 都好写。
2. Apache 2.0，Maven Central 仍活跃；4.x 当前 beta，不作为 Sprint 3 稳定依赖。
3. 比自己起 Jetty 少样板；比 MockWebServer 更适合复杂 request/response matching 和故障注入。

不选：

- MockWebServer：轻量，但主要来自 OkHttp 生态，引入额外客户端生态依赖；复杂重定向/场景状态表达不如 WireMock 直观。
- 自己起 Jetty：可控但样板多，容易把测试重点从 collector 变成 server fixture。

### 6.2 必测场景

1. 正常 HTTPS URL：WireMock 返回 HTML，提取 title/body，落库 `pending_review`。
2. 私网 IP：`http://127.0.0.1/admin` 被 `URL_BLOCKED_PRIVATE_IP` 拒绝。
3. DNS 解析到私网：mock `DnsResolver` 返回 `10.0.0.1`，即使 host 是公网域名也拒绝。
4. 重定向到私网：初始公网 URL 302 到 `http://127.0.0.1`，第二跳拒绝。
5. 重定向超过 5 跳：返回 `REDIRECT_TOO_DEEP`。
6. DNS rebinding：同一 host 第一次返回公网，第二次返回私网；每次连接前重新 resolve，并在第二跳拒绝。
7. 大响应：WireMock 用 `ResponseDefinitionTransformer` 或 streaming transformer 生成超过 5MB 内容，不在 repo 放 5MB fixture。
8. Content-Length 超限：header 大于 5MB，body 可很小，必须提前拒绝。
9. Read timeout：WireMock fixed delay 超过 30s；测试中把 read timeout 配成 100ms，避免慢测。
10. 非 HTML Content-Type：`application/pdf` 返回 `UNSUPPORTED_CONTENT_TYPE`。
11. 抽取失败：HTML 只有导航/脚本，没有正文，返回 `EXTRACT_FAILED`。
12. URL 去重：同 brand 相同 `canonical_url_hash` 第二次返回 `DUPLICATE_URL`。
13. RSS parse：合法 RSS/Atom 各一例；坏 XML 返回 `RSS_PARSE_FAILED`。
14. RSS item duplicate：feed 中已有 URL 不重复建 draft。

### 6.3 大文件测试实现

不要提交真实 5MB 文件。

推荐实现：

- WireMock transformer 根据 query 参数生成 N 字节响应：
  - `/large?bytes=5242881`
  - header `Content-Type: text/html`
  - 可选 header `Content-Length`
- 对无 Content-Length 场景，transformer 写 chunked body，collector 的 `LimitedInputStream` 应在超过 5MB 时中断。

## 7. Sprint 3 PR 拆分预估

| PR | 范围 | 预估 LOC | 依赖 |
| --- | --- | ---: | --- |
| D5a | URL 单点采集 API、SSRF 防护、HttpClient 5 安全 fetch、jsoup 正文提取、URL hash 去重、article_draft 落库、audit、WireMock 测试 | 650-850 | 无 |
| D5b | RSS feed 表结构、订阅 API、ROME parser、定时刷新 job、feed item 去重、逐条调用 D5a collector、audit、RSS 测试 | 550-750 | D5a |
| D5c | 原文/改写字段补齐、AI rewrite 调用 D4 LLM facade、diff 运行时接口、法务溯源 audit、diff 测试 | 500-700 | D5a |

如果 D5a 超 800 LOC，拆成：

- D5a-1：SSRF guard + safe fetch + WireMock tests。
- D5a-2：HTML extract + article_draft 落库 + API。

## 8. D5a 代码结构建议

包名：

```text
com.huanjing.geo.module.content.collector
```

核心类：

- `UrlCollectController`
- `UrlCollectService`
- `CollectorHttpClient`
- `UrlSafetyGuard`
- `DnsResolver`
- `IpRangeBlocklist`
- `ArticleContentExtractor`
- `CollectedArticle`
- `CollectorProperties`

配置：

```yaml
geo:
  collector:
    max-response-bytes: 5242880
    connect-timeout: 5s
    read-timeout: 30s
    max-redirects: 5
    user-agent: "SanheGeoCollector/1.0 (+https://sanhe.example.com/collector; contact=ops@sanhe.example.com)"
```

正文提取启发式 v1：

1. 删除 `script/style/nav/header/footer/aside/form`。
2. 优先选择 `article`、`main`、`[role=main]`、常见中文内容 class：`content/article/post/detail/text/rich`.
3. 对候选节点打分：中文字符数、段落数、链接密度、图片数量、标题邻近度。
4. 输出 markdown：保留 h1-h6、p、blockquote、ul/ol/li、table、pre/code、img。
5. 标题：优先 `og:title`，其次 `h1`，最后 `title` 去站点后缀。

抽取失败阈值：

- title 为空且正文低于 200 个中文/英文字符。
- 链接密度高于 0.6 且段落数低于 3。

## 9. 数据库建议

D5a migration：

```sql
ALTER TABLE article_draft_version
  ADD COLUMN source_kind varchar(20) NULL,
  ADD COLUMN source_url varchar(1024) NULL,
  ADD COLUMN canonical_url varchar(1024) NULL,
  ADD COLUMN canonical_url_hash char(64) NULL,
  ADD COLUMN source_title varchar(512) NULL,
  ADD COLUMN source_author varchar(255) NULL,
  ADD COLUMN source_published_at datetime NULL,
  ADD COLUMN source_fetched_at datetime NULL,
  ADD COLUMN source_content_markdown mediumtext NULL;

CREATE UNIQUE INDEX uk_article_draft_source_url_hash
  ON article_draft_version(brand_id, canonical_url_hash);
```

注意：如果 `article_draft_version` 当前没有 `brand_id`，不要为索引反查 article；应在 `content_articles` 或 draft 主表层建 source 表：

```sql
CREATE TABLE article_source_capture (
  id bigint PRIMARY KEY AUTO_INCREMENT,
  brand_id bigint NOT NULL,
  article_id bigint NULL,
  draft_version_id bigint NULL,
  source_kind varchar(20) NOT NULL,
  source_url varchar(1024) NOT NULL,
  canonical_url varchar(1024) NOT NULL,
  canonical_url_hash char(64) NOT NULL,
  source_content_markdown mediumtext NULL,
  fetched_at datetime NOT NULL,
  created_at datetime NOT NULL,
  UNIQUE KEY uk_brand_source_url_hash (brand_id, canonical_url_hash)
);
```

推荐：优先新建 `article_source_capture`，减少对既有 article version 表的侵入。

## 10. Sprint 3 接手 Checklist

D5a 第一 commit 只加 dependencies/properties 和 SSRF guard；先写 `UrlSafetyGuardTest`。所有外部 URL fetch 必须经过 `CollectorHttpClient`；redirect 应用层手动处理，每跳重新 DNS/IP 校验。audit detail 只写 hash/host/长度/状态，不写正文和完整 query。`803xx` 新错误码同步 `AuditOperationAspect` 集合。D5b feed fetch 复用 D5a safe fetch，不另开网络路径。
