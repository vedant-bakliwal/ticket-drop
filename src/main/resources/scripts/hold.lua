-- KEYS: available (int), per-user seats (hash), holds (hash id->"user|qty|expiryMs"), hold expiries (zset)
-- ARGV: user, qty, maxPerUser, holdId, expiry ms
local qty = tonumber(ARGV[2])
local mine = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
if mine + qty > tonumber(ARGV[3]) then return 'OVER_LIMIT' end
if tonumber(redis.call('GET', KEYS[1]) or '0') < qty then return 'SOLD_OUT' end
redis.call('DECRBY', KEYS[1], qty)
redis.call('HINCRBY', KEYS[2], ARGV[1], qty)
redis.call('HSET', KEYS[3], ARGV[4], ARGV[1] .. '|' .. qty .. '|' .. ARGV[5])
redis.call('ZADD', KEYS[4], ARGV[5], ARGV[4])
return 'HELD'
