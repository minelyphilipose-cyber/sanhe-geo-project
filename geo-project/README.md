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

### 3. 启动前端
```bash
cd geo-web
npm install
npm run dev
```

访问 http://localhost:3000

默认管理员账号: admin / admin123
