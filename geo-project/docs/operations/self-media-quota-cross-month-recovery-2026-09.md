# 2026-09 自媒体跨月额度生产恢复手册

本手册只适用于 2026-08-26 事故集合：批次 `35,37,38,39,40,41,42,43`、计划 `25,27,28,29,30,31,32`、共 412 个计划项。不得把批次或月份条件扩大为模糊匹配。

## 1. 冻结与确认

保持 `.env` 中：

```dotenv
GEO_SELF_MEDIA_AUTO_SCHEDULE_JOB_ENABLED=false
```

重建服务并确认运行中容器实际加载了开关：

```bash
sudo docker compose --env-file .env up -d --force-recreate geo-server
sudo docker exec geo-server sh -lc 'printf "%s\n" "${GEO_SELF_MEDIA_AUTO_SCHEDULE_JOB_ENABLED:-<unset>}"'
sudo docker logs geo-server --since 10m 2>&1 | grep -E 'project self-media (next-month pre-schedule|staggered schedule plans processed)' || true
```

输出必须为 `false`。再间隔数分钟查询一次目标月批次数量和最大 ID，确认没有增长。若仍增长，停止操作并先找出其他运行实例。

## 2. 只读门禁与事务修复

在生产项目目录执行：

```bash
chmod +x scripts/repair-self-media-quota-period-2026-09.sh
./scripts/repair-self-media-quota-period-2026-09.sh --check
```

门禁结果必须同时满足：

- 412 个计划项、8 个批次；全部为 `planned/reserved`。
- `article_id`、`schedule_id`、生成批次、生成任务和生成 claim 均为空。
- 412 条额度流水、合计 412；全部为 `project_self_media_plan/reserved/2026-08`。
- 每个受影响 8 月用量汇总行存在，且 `used_count - refund_count >= 0`。
- 计划 `25,27,28,29,30,31,32` 全部为 `completed`。

任何一项不符都不得执行写操作。确认后执行：

```bash
BACKUP_DIR=/opt/geo-backups ./scripts/repair-self-media-quota-period-2026-09.sh --apply
```

脚本会先备份 5 张表，再在单事务中逐步校验行数。任何门禁、非负约束或更新行数不符都会使 mysql 客户端报错并断开，未提交事务随连接关闭而回滚。执行完成后保存脚本输出和备份文件路径。

不要修改已退款的历史告警流水 `ledgerId=4070`。

## 3. 部署修复版本

先部署包含显式额度日期修复的 `geo-server`，任务开关仍保持 `false`。启动后确认健康状态，并检查日志中没有启动期额度异常：

```bash
sudo docker inspect geo-server --format 'status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{end}} started={{.State.StartedAt}} image={{.Config.Image}}'
sudo docker logs geo-server --since 10m 2>&1 | grep -E 'quota|self-media|Exception' || true
```

## 4. 重建手动批次 43

批次 43 对应 `project_id=22`。先使用现有页面执行预览，或调用：

```http
POST /api/projects/22/self-media-schedules/auto-preview
Content-Type: application/json

{
  "targetMonth": "2026-09",
  "selfMediaAccountIds": [],
  "scheduleStrategy": "platform_specific"
}
```

空账号列表会由服务端选择该品牌全部 `active` 且未删除账号。预览必须包含知乎，并得到：抖音 8、头条 13、微信 13、知乎 4，总计 38。数量不符时不要创建。

确认预览后，通过页面创建，或使用新的事故恢复幂等键调用：

```http
POST /api/projects/22/self-media-schedules/auto-create
Idempotency-Key: recovery-2026-09-project-22-<唯一值>
Content-Type: application/json

{
  "targetMonth": "2026-09",
  "selfMediaAccountIds": [],
  "scheduleStrategy": "platform_specific"
}
```

服务会复用月度唯一批次 43；旧计划项保留为 `cancelled/refunded`，新计划项使用新的 UUID 幂等键。

## 5. 验证新额度周期

在恢复任务前验证批次 43 新计划项和额度流水：

```sql
SELECT i.platform, COUNT(*) AS item_count, l.period_key, l.status
FROM project_self_media_schedule_item i
JOIN company_channel_quota_ledger l
  ON l.biz_type = 'project_self_media_plan'
 AND l.biz_id = CAST(i.id AS CHAR)
WHERE i.batch_id = 43
  AND i.status = 'planned'
GROUP BY i.platform, l.period_key, l.status
ORDER BY i.platform;
```

必须只有 `2026-09/reserved`，合计 38，平台数量为上述 8/13/13/4。

## 6. 恢复自动计划

将 `.env` 改回：

```dotenv
GEO_SELF_MEDIA_AUTO_SCHEDULE_JOB_ENABLED=true
```

必须重建容器，不能只改文件：

```bash
sudo docker compose --env-file .env up -d --force-recreate geo-server
sudo docker exec geo-server sh -lc 'printf "%s\n" "${GEO_SELF_MEDIA_AUTO_SCHEDULE_JOB_ENABLED:-<unset>}"'
sudo docker logs -f --since 2m geo-server
```

观察 7 个重置计划和原 6 个 pending 计划。它们应进入 `completed`，或留下明确的可处理失败原因。

## 7. 最终验收 SQL

```sql
-- 9 月活跃计划不得记入其他额度周期。
SELECT COUNT(*) AS wrong_active_ledger_count
FROM project_self_media_schedule_batch b
JOIN project_self_media_schedule_item i ON i.batch_id = b.id
JOIN company_channel_quota_ledger l
  ON l.biz_type = 'project_self_media_plan'
 AND l.biz_id = CAST(i.id AS CHAR)
WHERE CAST(b.target_month AS BINARY) = CAST('2026-09' AS BINARY)
  AND i.quota_reservation_status IN ('reserved', 'transferred')
  AND l.status IN ('reserved', 'confirmed')
  AND CAST(l.period_key AS BINARY) <> CAST('2026-09' AS BINARY);

-- 用量汇总必须等于有效流水合计。
SELECT u.company_id, u.channel_code, u.period_type, u.period_key,
       u.used_count,
       COALESCE(SUM(CASE WHEN l.status IN ('reserved','confirmed') THEN l.delta_count ELSE 0 END), 0) AS ledger_used
FROM company_channel_quota_usage u
LEFT JOIN company_channel_quota_ledger l
  ON l.company_id = u.company_id
 AND l.channel_code = u.channel_code
 AND l.period_type = u.period_type
 AND l.period_key = u.period_key
WHERE u.period_key IN ('2026-08', '2026-09')
  AND u.channel_code LIKE 'self_media:%'
GROUP BY u.id, u.company_id, u.channel_code, u.period_type, u.period_key, u.used_count
HAVING u.used_count <> ledger_used;

SELECT id, project_id, status, retry_count, failure_message
FROM project_self_media_schedule_plan
WHERE target_month = '2026-09'
ORDER BY id;
```

`wrong_active_ledger_count` 必须为 0；用量一致性查询必须返回空集。最后检查日志不再出现跨月额度耗尽、预占不存在或额度释放异常，再结束事故恢复。
