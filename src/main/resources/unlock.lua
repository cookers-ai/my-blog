--锁的key
local lockKey = Keys[1]
--当前线程标识
local threadId = ARGV[1]
--获取线程标识
local id=redis.call('get',Keys[1])
--比较线程标识与锁中线程标识是否一致
if id == threadId then
    redis.call('del',lockKey)
else
    return 0
end