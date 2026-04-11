# 第3步（小步3+4）实施进度

## 进入本小步前累计已完成：
- 已完成第3步小步1+2：
  - V8 公司主数据约束触发器已落库（owner_type/partner_id/status）。
  - 公司后端与前端删除链路已打通，且公司下有品牌时禁止删除。

## 本小步新增完成：
- 小步3（品牌 CRUD 收口）：
  - 后端新增品牌删除接口：`DELETE /api/brands/{id}`。
  - 删除保护：品牌下存在项目时禁止删除。
  - 品牌状态校验补齐：仅允许 `draft/active/archived`。
  - 品牌写操作增加范围校验，避免越权编辑/删除他人品牌。
  - 前端客户详情页新增“删除品牌”按钮（带二次确认、删除后刷新列表）。
- 小步4（项目 CRUD + 归属模式）：
  - 后端新增项目删除接口：`DELETE /api/projects/{id}`。
  - 新增 V9 项目约束触发器（owner_type/partner_id/status/stage）。
  - 服务层补齐项目归属模式校验：
    - `direct` 禁止绑定 `partner_id`
    - `partner/joint` 必须绑定 `partner_id`
    - 项目 `partner_id` 必须与所属公司 `partner_id` 一致（非 direct）
  - 服务层补齐项目主字段校验：
    - `package_type` 仅允许 `trial_6980/standard_12800/growth_26800`
    - `package_price/service_months` 必须为正
    - `status/stage` 必须在允许集合内
  - 前端项目列表与详情页新增删除操作（带二次确认、删除后刷新/回跳）。
- 验证：
  - 前端 `npm run build` 通过。

## 本小步未完成/遗留：
- 本环境无法执行 Maven，后端编译与启动联调需本机验证。
- 第3步小步5（状态/阶段流转规则）尚未实现。
- 第3步小步6（基础操作日志）尚未实现。

## 下一小步输入前提（依赖）：
- 数据库执行到 `V9`。
- 准备品牌下有/无项目两类样本，用于验证品牌删除保护。
- 准备项目 `direct/partner/joint` 三类样本，验证归属模式与一致性校验。
