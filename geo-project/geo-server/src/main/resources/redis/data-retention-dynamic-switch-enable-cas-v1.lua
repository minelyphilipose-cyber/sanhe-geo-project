local MAX_VERSION = '9223372036854775807'
local ENABLE_CAS_GRACE_MILLIS = 60000
local MAX_SAFE_INTEGER = '9007199254740991'

local function valid_version(value)
  if not value or not string.match(value, '^[1-9][0-9]*$') then return false end
  if string.len(value) < string.len(MAX_VERSION) then return true end
  if string.len(value) > string.len(MAX_VERSION) then return false end
  return value <= MAX_VERSION
end

local function valid_uuid(value)
  return value and string.match(value,
    '^[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]%-[0-9a-f][0-9a-f][0-9a-f][0-9a-f]%-[1-5][0-9a-f][0-9a-f][0-9a-f]%-[89ab][0-9a-f][0-9a-f][0-9a-f]%-[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]$') ~= nil
end

local function valid_safe_integer(value)
  if not value or not string.match(value, '^[1-9][0-9]*$') then return false end
  if string.len(value) < string.len(MAX_SAFE_INTEGER) then return true end
  if string.len(value) > string.len(MAX_SAFE_INTEGER) then return false end
  return value <= MAX_SAFE_INTEGER
end

local function valid_time(value)
  if not value or not string.match(value,
    '^[0-9][0-9][0-9][0-9]%-[0-9][0-9]%-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]%.[0-9][0-9][0-9]Z$') then return false end
  local year, month, day = tonumber(string.sub(value, 1, 4)), tonumber(string.sub(value, 6, 7)), tonumber(string.sub(value, 9, 10))
  local hour, minute, second = tonumber(string.sub(value, 12, 13)), tonumber(string.sub(value, 15, 16)), tonumber(string.sub(value, 18, 19))
  if year < 1000 or month < 1 or month > 12 or hour > 23 or minute > 59 or second > 59 then return false end
  local days = {31,28,31,30,31,30,31,31,30,31,30,31}
  if (year % 4 == 0 and year % 100 ~= 0) or year % 400 == 0 then days[2] = 29 end
  return day >= 1 and day <= days[month]
end

local function valid_payload(change_id, version, enabled, changed_at)
  return valid_uuid(change_id) and valid_version(version) and
    (enabled == '0' or enabled == '1') and valid_time(changed_at)
end

local function inspect(key)
  if redis.call('EXISTS', key) == 0 then return {'M', '', '', '', ''} end
  local key_type = redis.call('TYPE', key)
  if key_type['ok'] ~= 'hash' or redis.call('HLEN', key) ~= 4 then return {'X', '', '', '', ''} end
  local values = redis.call('HMGET', key, 'change_id', 'version', 'enabled', 'changed_at')
  if not valid_payload(values[1], values[2], values[3], values[4]) then return {'X', '', '', '', ''} end
  return {'V', values[1], values[2], values[3], values[4]}
end

local function version_compare(left, right)
  if string.len(left) ~= string.len(right) then return string.len(left) < string.len(right) and -1 or 1 end
  if left == right then return 0 end
  return left < right and -1 or 1
end

local function exact(state, change_id, version, enabled, changed_at)
  return state[1] == 'V' and state[2] == change_id and state[3] == version and
    state[4] == enabled and state[5] == changed_at
end

local function result(code, state)
  return table.concat({code, state[1], state[2], state[3], state[4], state[5]}, '\t')
end

if not valid_payload(ARGV[1], ARGV[2], ARGV[3], ARGV[4]) or ARGV[3] ~= '1' then
  return result('MALFORMED_CURRENT', {'X', '', '', '', ''})
end
if ARGV[5] ~= 'M' and ARGV[5] ~= 'V' then return result('EXPECTED_MISMATCH', {'X', '', '', '', ''}) end
if ARGV[5] == 'V' and not valid_payload(ARGV[6], ARGV[7], ARGV[8], ARGV[9]) then
  return result('EXPECTED_MISMATCH', {'X', '', '', '', ''})
end
if not valid_safe_integer(ARGV[10]) then
  return result('REDIS_TIME_ROLLBACK', {'X', '', '', '', ''})
end

local current = inspect(KEYS[1])
if exact(current, ARGV[1], ARGV[2], ARGV[3], ARGV[4]) then return result('ALREADY_APPLIED', current) end
local current_time = redis.call('TIME')
local current_millis = tonumber(current_time[1]) * 1000 + math.floor(tonumber(current_time[2]) / 1000)
local approved_millis = tonumber(ARGV[10])
if current_millis < approved_millis then return result('REDIS_TIME_ROLLBACK', current) end
if current_millis > approved_millis + ENABLE_CAS_GRACE_MILLIS then
  return result('ENABLE_APPROVAL_EXPIRED', current)
end
if current[1] == 'X' then return result('MALFORMED_CURRENT', current) end
if current[1] == 'V' then
  local comparison = version_compare(current[3], ARGV[2])
  if comparison > 0 then return result('STALE_TARGET', current) end
  if comparison == 0 then return result('SAME_VERSION_DIFFERENT_PAYLOAD', current) end
end

local expected_matches = ARGV[5] == 'M' and current[1] == 'M'
if ARGV[5] == 'V' then expected_matches = exact(current, ARGV[6], ARGV[7], ARGV[8], ARGV[9]) end
if not expected_matches then return result('EXPECTED_MISMATCH', current) end

redis.call('HSET', KEYS[1],
  'change_id', ARGV[1], 'version', ARGV[2], 'enabled', ARGV[3], 'changed_at', ARGV[4])
return result('APPLIED', {'V', ARGV[1], ARGV[2], ARGV[3], ARGV[4]})
