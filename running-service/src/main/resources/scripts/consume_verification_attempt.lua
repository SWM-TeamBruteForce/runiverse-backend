if redis.call('EXISTS', KEYS[1]) == 0 then
    return {'NOT_FOUND'}
end
local attempts = redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
if attempts > tonumber(ARGV[2]) then
    redis.call('DEL', KEYS[1])
    return {'EXHAUSTED'}
end
return {'AVAILABLE', redis.call('HGET', KEYS[1], ARGV[3])}
