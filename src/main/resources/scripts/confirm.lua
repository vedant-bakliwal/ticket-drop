-- KEYS: holds, hold expiries, sold. ARGV: holdId, now ms. Returns 1 if the hold became a sale.
local v = redis.call('HGET', KEYS[1], ARGV[1])
if not v then return 0 end
local _, _, user, qty, exp = string.find(v, '^(.-)|(%d+)|(%d+)$')
if tonumber(exp) <= tonumber(ARGV[2]) then return 0 end
redis.call('HDEL', KEYS[1], ARGV[1])
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('INCRBY', KEYS[3], tonumber(qty))
return 1
