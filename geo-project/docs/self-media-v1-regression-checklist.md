# 自媒体分发 v1 回归清单

本文档用于在不连接真实抖音/微信平台的前提下，完成第一阶段代码逻辑验证；生产受控验证应在这些检查通过后再执行。

## 第一阶段：本地代码验证

目标：

- 验证抖音图文请求参数组装、图片素材映射、token 过期重试、平台错误映射。
- 验证公众号草稿创建参数、自动发布开关、发布状态回查、微信错误码处理。
- 验证排期创建的幂等、平台能力门控、状态流转基础策略。
- 确认测试过程不依赖真实 `AppId`、`Secret`、access token 或公网回调地址。

执行命令：

```powershell
cd D:\code\sanhe-geo-project\geo-project
.\scripts\self-media-phase1-check.ps1
```

等价 Maven 命令：

```powershell
cd D:\code\sanhe-geo-project\geo-project\geo-server
mvn "-Dtest=com.huanjing.geo.module.content.douyin.DouyinImageTextAdapterTest,com.huanjing.geo.module.content.douyin.DouyinMediaServiceTest,com.huanjing.geo.module.content.douyin.DouyinTokenServiceTest,com.huanjing.geo.module.content.douyin.client.RealDouyinClientTest,com.huanjing.geo.module.content.douyin.client.DouyinErrorMapperTest,com.huanjing.geo.module.content.douyin.client.DouyinDtoDeserializationTest,com.huanjing.geo.module.content.service.adapter.WechatMpAdapterTest,com.huanjing.geo.module.content.wechat.WechatApiErrorHandlerTest,com.huanjing.geo.module.content.wechat.WechatTokenAwareExecutorTest,com.huanjing.geo.module.content.service.SelfMediaPublishScheduleServiceTest,com.huanjing.geo.module.content.service.DistributionTaskStatePolicyTest" test
```

通过标准：

- Maven 退出码为 `0`。
- 测试日志中没有真实 `api.weixin.qq.com`、`open.douyin.com`、`open-sandbox.douyin.com` 调用。
- 抖音请求快照不包含 access token。
- 公众号草稿请求快照不包含 access token 或正文 HTML。
- 公众号自动发布未开启时，请求 `publishAction=publish` 会被拒绝。
- 重复请求幂等键不会创建重复排期。

## 第一阶段覆盖项

抖音图文：

- `DouyinImageTextAdapterTest`：平台账号校验、图片数量校验、文案长度校验、请求参数组装、token 失效后重试、错误类型映射、审核状态映射。
- `DouyinMediaServiceTest`：素材归属校验、图片类型/大小校验、素材上传幂等映射、并发锁、上传 token 失效重试。
- `RealDouyinClientTest`：通过本地内置 HTTP server 验证真实客户端的 form、multipart、JSON 请求格式，不调用抖音公网。
- `DouyinErrorMapperTest` / `DouyinDtoDeserializationTest`：错误码和响应反序列化。

公众号：

- `WechatMpAdapterTest`：封面必填、草稿文章字段组装、草稿模式、自动发布开关、发布状态回查。
- `WechatApiErrorHandlerTest`：微信错误码、IP 白名单错误处理。
- `WechatTokenAwareExecutorTest`：authorizer token 失效后的刷新与重试。

通用分发：

- `SelfMediaPublishScheduleServiceTest`：排期幂等、账号绑定、平台能力门控、重复排期阻断。
- `DistributionTaskStatePolicyTest`：分发任务状态组合合法性。

## 进入生产受控验证前检查

- 第一阶段脚本全部通过。
- 生产环境测试账号已配置白名单。
- 批量自动分发入口关闭，仅允许手动单篇触发。
- 公众号优先只创建草稿和预览，不直接群发。
- 抖音仅使用测试账号发布低风险测试内容。
- 验证期间有人观察应用日志、平台返回、数据库发布记录。
