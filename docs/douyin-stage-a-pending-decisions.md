# Douyin Stage A Pending Decisions

Last updated: 2026-05-05

This table tracks product and operations decisions that can block or shape Douyin image-text integration. Stage A mock development can start before these are closed, but real OAuth and real publishing cannot proceed until the relevant operation-side dependencies are resolved.

| No. | Decision Item | Owner | Required By | Current Status | Impact If Open | Notes / Acceptance Criteria |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Douyin Open Platform主体认证后台具体字段与材料 | 运营 | A.8真实 OAuth 联调前 | 待确认 | 无法确认应用注册材料完整性 | 输出主体认证提交材料清单和后台截图/字段说明。 |
| 2 | 行业类目选择及是否触发特殊行业准入 | 运营 | scope申请前 | 待确认 | scope审核可能被退回或要求补充资质 | SEO/营销服务对应类目需以开放平台后台可选项为准。 |
| 3 | `video.create.bind` 使用场景说明最终文案 | 运营 + 法务 | scope申请前 | 待确认 | 影响权限审核通过率 | 基于v2模板出最终版，说明用户授权后由系统代发图文、内容来源、审核与用户操作路径。 |
| 4 | 网站应用ICP备案、隐私政策页、用户协议页是否就绪 | 产品 | 应用上线前 | 待确认 | 应用上线/权限审核可能无法提交 | 给出可访问HTTPS URL，页面内容覆盖数据授权、内容发布、账号解绑。 |
| 5 | 抖音是否提供沙箱环境 | 运营 | A.8真实联调计划前 | 待确认 | 决定真实联调风险和Mock覆盖强度 | 如无沙箱，Stage A Mock必须覆盖OAuth、token过期、限频、发布失败、审核状态UNKNOWN。 |
| 6 | 单账号日发布配额准确数字 | 运营 | A.6 Adapter提交前置校验完善前 | 待确认 | 无法做本地日配额软拦截 | 后台确认后写入 `self_media_account.extra_json.daily_publish_quota` 或配置表。 |
| 7 | Stage A 是否提供撤回/删除能力 | 产品 | A.6 Adapter设计冻结前 | 待确认 | 影响状态机、按钮和API预留 | Stage A默认不做，若需要需确认官方API和状态流。 |
| 8 | Stage B内容策略选型 | 产品 | Stage B启动前 | 待确认 | 影响图文质量和图片生成工作量 | 候选：AI排版生图、正文图提取+智能裁剪、运营手动选图。 |
| 9 | 图文发布默认图片张数、话题、公开状态、下载权限 | 产品 | A.6 Adapter请求模型落库前 | 待确认 | 影响 `TargetContext.SelfMediaTarget` 字段使用和前端表单 | Stage A可先用最小字段：图片数组、标题/文案、公开状态默认值。 |
| 10 | 拒审后二次编辑重提流程 | 产品 | 审核状态展示前 | 待确认 | 影响幂等requestId、任务复用还是新建任务 | Stage A真实审核查询若不可得，拒审流先不开放；Mock可预留场景。 |
| 11 | Stage A灰度范围与白名单策略 | 产品 + 运营 | A.7端到端Mock闭环前 | 待确认 | 实验功能可能误开放给不应使用的客户 | 确认按 `brand_id`、`user_id` 或角色白名单控制，并确定实验提示文案。 |
| 12 | 测试用/联调用抖音企业账号 | 运营 | A.8真实联调前 | 待确认 | 没有真实账号无法做端到端联调验证 | 至少准备1个企业认证账号，配合应用注册后做授权测试。 |

## Operational Dependency Summary

| Dependency | Code Can Proceed? | Blocks |
| --- | --- | --- |
| 主体认证 | Yes | A.8真实 OAuth |
| 应用注册和 `client_key/client_secret` | Yes until mock OAuth is complete | A.8真实 OAuth |
| `video.create.bind` scope通过 | Yes until mock publish is complete | A.9真实图片上传和创建图文 |
| 日发布配额 | Yes | 精准本地配额软校验 |
| 沙箱可用性 | Yes | 真实联调方案和Mock覆盖边界 |

## Tracking Rule

Each item should be assigned a named owner and target date before A.7 completes. If any A.8/A.9 dependency remains open, code work stops at Mock闭环 and does not switch to real OpenAPI calls.
