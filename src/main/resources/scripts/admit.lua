-- KEYS: queue, admitted. ARGV: max, maxActive, now ms, access ttl ms
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[3])
local slots = math.min(tonumber(ARGV[1]), tonumber(ARGV[2]) - redis.call('ZCARD', KEYS[2]))
if slots <= 0 then return 0 end
local users = redis.call('ZRANGE', KEYS[1], 0, slots - 1)
local expiry = tonumber(ARGV[3]) + tonumber(ARGV[4])
for _, u in ipairs(users) do
  redis.call('ZADD', KEYS[2], expiry, u)
  redis.call('ZREM', KEYS[1], u)
end
return #users
