local current = redis.call('TIME')
return current[1] .. ':' .. current[2]
