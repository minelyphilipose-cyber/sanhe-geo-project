# 第3步（小步1+2）实施进度

## 进入本小步前累计已完成：
- 第2步累计已完成：账号/角色/权限模型可用，后端接口权限真实生效，合伙人数据隔离生效。
- 前端已有客户/品牌/项目路由与 API 基础能力。

## 本小步新增完成：
- 小步1（主数据约束补齐）：
  - 新增迁移脚本 `V8__phase3_company_constraints.sql`。
  - 在数据库层新增 `company` 触发器约束（insert/update）：
    - `owner_type` 仅允许 `direct/partner/joint`
    - `direct` 禁止绑定 `partner_id`
    - `partner/joint` 必须绑定 `partner_id`
    - `status` 仅允许 `potential/signed/inactive`
  - 在服务层同步加入同等业务校验，返回更友好错误信息。
- 小步2（公司完整 CRUD 收口）：
  - 后端新增删除接口：`DELETE /api/companies/{id}`。
  - 删除保护：公司下存在品牌时禁止删除（避免主数据断链）。
  - 前端补全删除能力：
    - `api/customer.ts` 新增 `deleteCompany`
    - 客户列表新增“删除”操作（带二次确认）
    - 客户详情新增“删除客户”操作（带二次确认、成功后回列表）
  - 前端构建验证通过（`npm run build`）。

## 本小步未完成/遗留：
- 后端 Maven 编译与启动联调尚未在本环境执行（当前环境无 `mvn`）。
- 品牌/项目的“删除”与完整 CRUD 收口待下一小步处理。
- 项目状态/阶段流转规则与操作日志待后续小步处理。

## 下一小步输入前提（依赖）：
- 数据库执行到 `V8` 成功。
- 准备一组 company 测试数据（`direct` 与 `partner/joint` 各至少一条）。
- 对删除场景准备“有品牌/无品牌”两类公司样本。
