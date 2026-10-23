-- KEYS: capacity, available, sold. ARGV: capacity. Creates the event once; never resets a live sale.
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('SET', KEYS[1], ARGV[1])
redis.call('SET', KEYS[2], ARGV[1])
redis.call('SET', KEYS[3], 0)
return 1
