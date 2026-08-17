local MAX_VERSION = '9223372036854775807'

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

local function inspect(key)
  if redis.call('EXISTS', key) == 0 then return {'M', '', '', '', ''} end
  local key_type = redis.call('TYPE', key)
  if key_type['ok'] ~= 'hash' or redis.call('HLEN', key) ~= 4 then return {'X', '', '', '', ''} end
  local values = redis.call('HMGET', key, 'change_id', 'version', 'enabled', 'changed_at')
  if not valid_uuid(values[1]) or not valid_version(values[2]) or
      (values[3] ~= '0' and values[3] ~= '1') or not valid_time(values[4]) then
    return {'X', '', '', '', ''}
  end
  return {'V', values[1], values[2], values[3], values[4]}
end

local global_state = inspect(KEYS[1])
local domain_state = inspect(KEYS[2])
return table.concat({
  global_state[1], global_state[2], global_state[3], global_state[4], global_state[5],
  domain_state[1], domain_state[2], domain_state[3], domain_state[4], domain_state[5]
}, '\t')
