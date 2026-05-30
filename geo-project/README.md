# 幻境AI · GEO 托管交付系统

内部使用的高度自动化 GEO 托管交付系统。

## 项目结构

```
geo-project/
├── geo-web/        # 前端 Vue3 + Vite + Element Plus + Tailwind
├── geo-server/     # 后端 Spring Boot 3 + MyBatis-Plus + MySQL + Redis
└── docker-compose.yml
```

## 快速启动

### 1. 启动基础设施
```bash
docker-compose up -d mysql redis minio
```

### 2. 启动后端
```bash
cd geo-server
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

如需联调品牌 GEO 站点发布通道,先在项目根目录启动无依赖 mock:

```bash
python tools/brand-geo-site-mock-server.py
```

dev 配置默认请求 `http://127.0.0.1:18080/api/v1/content`,验收清单见 `docs/phase2a-brand-geo-site-acceptance.md`。

### 3. 启动前端
```bash
cd geo-web
npm install
npm run dev
```

访问 http://localhost:3000

默认管理员账号: admin / admin123

## 自媒体分发 v1 文档

- [本地助手 v1 交付说明](docs/self-media-local-helper-v1-delivery.md)
- [自媒体分发 v1 回归清单](docs/self-media-v1-regression-checklist.md)

## Presale PR2 说明

- 本次为内部系统先跑通实现,裁判管线同步执行。
- 整单生成耗时预计增加到约 2 倍量级(11min -> 35min)。
- 裁判 tone 枚举当前定义为 `OBJECTIVE|PROMOTIONAL|MIXED|UNKNOWN`。
- 如后续需要优化,可参考下方异步化备选方案原则。

### 异步化备选方案(原则性思路)

- 将认知/对比裁判从主流程同步链路拆出为独立任务。
- 主流程先完成基础快照并标记可用,裁判完成后增量刷新指标快照。
- 保持幂等键为 `prompt_result_id`,失败重试与补偿由任务层统一处理。

## Presale TODO

- [ ] 统一 `PresaleJudgeService` 与 `PlatformIntentBreakdownBuilder` 的 category 常量定义，抽取 `PresaleJudgeCategory` 枚举(包含 templateName/dbCode)避免同名异值混淆。
