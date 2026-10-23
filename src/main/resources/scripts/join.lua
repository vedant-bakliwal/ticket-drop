-- KEYS: queue (zset user->seq), admitted (zset user->access expiry ms), seq (counter)
-- ARGV: user, now ms
local exp = redis.call('ZSCORE', KEYS[2], ARGV[1])
if exp and tonumber(exp) > tonumber(ARGV[2]) then
  return {'ADMITTED', exp}
end
if exp then redis.call('ZREM', KEYS[2], ARGV[1]) end
if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then
  redis.call('ZADD', KEYS[1], redis.call('INCR', KEYS[3]), ARGV[1])
end
return {'QUEUED', tostring(redis.call('ZRANK', KEYS[1], ARGV[1]))}
