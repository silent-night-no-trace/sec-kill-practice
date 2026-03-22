redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])

local current = redis.call('ZCARD', KEYS[1])
local limit = tonumber(ARGV[4])
if current >= limit then
    redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[5]))
    return 0
end

redis.call('ZADD', KEYS[1], tonumber(ARGV[2]), ARGV[3])
redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[5]))
return 1
