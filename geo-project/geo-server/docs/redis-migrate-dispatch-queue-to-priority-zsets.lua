-- One-time dispatch queue migration.
--
-- Purpose:
--   Move legacy dispatch queue members from one zset to per-priority zsets.
--   Migrated members become immediately visible in the new queue.
--
-- Run only during a stopped-writer window:
--   1. Stop dispatch workers and dispatch task intake.
--   2. Confirm the legacy queue is static.
--   3. Run this script.
--   4. Deploy code that reads/writes per-priority queue keys.
--   5. Start workers.
--
-- KEYS[1] = legacy queue key, for example geo:dispatch:queue:zset
-- ARGV[1] = bucket width used by legacy score encoding, default 1000000000000
-- ARGV[2] = migrated legacy key suffix, default migrated:<yyyyMMddHHmmss>
-- ARGV[3] = availableAtMillis for migrated tasks, default current Redis TIME millis
--
-- Notes:
--   - The script is idempotent before the final rename: ZRANGEBYSCORE reads
--     the legacy key, ZADD overwrites the same member in the target priority
--     key, and the legacy key remains intact until the final RENAMENX.
--   - The final RENAMENX removes the legacy hot key from the old path while
--     retaining the original zset for rollback; no DEL is performed.
--   - The legacy key is renamed instead of deleted so rollback inspection remains
--     possible for one or two days.

local legacyKey = KEYS[1]
local bucketWidth = tonumber(ARGV[1]) or 1000000000000
local migratedSuffix = ARGV[2]
local availableAtMillis = tonumber(ARGV[3])

if not migratedSuffix or migratedSuffix == '' then
  local now = redis.call('TIME')
  migratedSuffix = 'migrated:' .. now[1]
end

if not availableAtMillis then
  local now = redis.call('TIME')
  availableAtMillis = now[1] * 1000 + math.floor(now[2] / 1000)
end

local rows = redis.call('ZRANGEBYSCORE', legacyKey, '-inf', '+inf', 'WITHSCORES')
local migrated = 0

for i = 1, #rows, 2 do
  local member = rows[i]
  local score = tonumber(rows[i + 1]) or 0
  local priority = math.floor(score / bucketWidth)
  if priority < 0 then
    priority = 0
  end
  local targetKey = legacyKey .. ':p' .. priority
  redis.call('ZADD', targetKey, availableAtMillis, member)
  migrated = migrated + 1
end

if redis.call('EXISTS', legacyKey) == 1 then
  local migratedKey = legacyKey .. ':' .. migratedSuffix
  redis.call('RENAMENX', legacyKey, migratedKey)
end

return migrated
