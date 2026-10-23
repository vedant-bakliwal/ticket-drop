-- KEYS: holds, hold expiries, available, per-user seats. ARGV: now ms. Returns seats released.
local released = 0
for _, id in ipairs(redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])) do
  local v = redis.call('HGET', KEYS[1], id)
  if v then
    local _, _, user, qty = string.find(v, '^(.-)|(%d+)|')
    qty = tonumber(qty)
    redis.call('HDEL', KEYS[1], id)
    redis.call('INCRBY', KEYS[3], qty)
    redis.call('HINCRBY', KEYS[4], user, -qty)
    released = released + qty
  end
  redis.call('ZREM', KEYS[2], id)
end
return released
