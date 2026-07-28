package com.itheima.big_event.utils;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itheima.big_event.pojo.Article;
import com.itheima.big_event.pojo.PageBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    /**
     * 缓存设置
     * key:Redis的key
     * value:缓存的值
     * time:过期时间
     * timeUnit:时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }
    /**
     * 缓存设置，逻辑过期
     * key:Redis的key
     * value:缓存的值
     * time:过期时间
     * timeUnit:时间单位
         */
    public void setWithLogicalExpie(String key, Object value, Long time, TimeUnit timeUnit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
       redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
       redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
        // 物理过期时间作为兜底
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData),
                time * 3, timeUnit);
    }
    /**
     * 缓存击穿问题解决：互斥锁
     * keyPrefix:Redis的key前缀
     * id:文章id
     * clazz:缓存的类
     * function:查询数据库的方法
     * time:过期时间
     * timeUnit:时间单位
         */
    public <T,ID> T articleDetailMutexLock(String keyPrefix, ID id, Class<T> clazz, Function<ID,T> function,
                                           Long time, TimeUnit timeUnit){
        String keyRedis = keyPrefix + id;
        long expireTime = time;
        //空值过期时间为1分钟
        long emptyExpireTime = 60;

        //取出redis中的JSON
        String json = stringRedisTemplate.opsForValue().get(keyRedis);
        //判断是否存在redis中
        if(StrUtil.isNotBlank(json)){
            //存在则返回
            return JSONUtil.toBean(json, clazz);
        }
        //判断是否存在redis中，且为空值
        if("".equals(json)){
            return null;
        }
        String lockKeyRedis = "Lock:"+keyPrefix+ id;
        T t = null;

        // 获取锁失败 → 休眠重试（不持有锁，不需要释放）
        if (!tryLock(lockKeyRedis)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("缓存重建被中断", e);
            }
            return articleDetailMutexLock(keyPrefix, id, clazz, function, time, timeUnit);
        }

        // 获取锁成功 → 只有这个路径才负责释放锁
        try {
            // Double Check：前一个线程可能已重建完成
            json = stringRedisTemplate.opsForValue().get(keyRedis);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, clazz);
            }
            if ("".equals(json)) {
                return null;
            }

            // 查询数据库
            t = function.apply(id);
            if (t == null) {
                // 写入空值到redis中，过期时间为1分钟
                stringRedisTemplate.opsForValue().set(keyRedis, "", emptyExpireTime, TimeUnit.SECONDS);
                return null;
            }

            // 设置过期时间（随机偏移防止缓存雪崩）
            Random random = new Random();
            long expireSeconds = timeUnit.toSeconds(time);
            expireSeconds += random.nextInt(3600);
            this.set(keyRedis, t, expireSeconds, TimeUnit.SECONDS);
        } finally {
            releaseLock(lockKeyRedis);
        }

        return t;
    }
    /**
     * 缓存击穿问题解决：逻辑过期
     * keyPrefix:Redis的key前缀
     * id:文章id
     * clazz:缓存的类
     * function:查询数据库的方法
     * expireSeconds:过期时间（秒）
     */
    public <T, ID> T queryWithLogicalExpire(
            String keyPrefix, ID id, Class<T> clazz,
            Function<ID, T> function, Long expireSeconds) {

        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 1. 缓存未命中（首次查询，需要预热或者返回 null）
        if (StrUtil.isBlank(json)) {
            log.warn("逻辑过期缓存未命中，降级互斥锁查询，key: {}", key);
            return articleDetailMutexLock(keyPrefix, id, clazz, function, expireSeconds, TimeUnit.SECONDS);
        }

        // 2. 命中，解析 RedisData
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T data = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 3. 判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回
            return data;
        }

        // 4. 逻辑过期，尝试获取互斥锁（防止缓存重建竞争）
        String lockKey = "lock:" + key;
        if (tryLockWithRetry(lockKey, 3, 100)) {
            // 获取锁成功，开启异步线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    T newData = function.apply(id);
                    // 写入缓存，逻辑过期时间重置
                    RedisData newRedisData = new RedisData();
                    newRedisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
                    newRedisData.setData(newData);
                    // 设置物理逻辑过期时间作为兜底
                    stringRedisTemplate.opsForValue()
                            .set(key, JSONUtil.toJsonStr(newRedisData), 24, TimeUnit.HOURS);
                } catch (Exception e) {
                    log.error("逻辑过期缓存重建失败，key:{}", key, e);
                } finally {
                    releaseLock(lockKey);
                }
            });
        }

        return data;
    }
    /**
     * 列表数据缓存（支持多参数）
     * @param keyPrefix   缓存前缀（如 "homeList:"）
     * @param cacheKey    拼接好的业务 key（如 "1:10:5:newest"）
     * @param clazz       列表内元素类型（用于反序列化 PageBean 中的泛型）
     * @param function    数据库查询回调，返回 PageBean<T>
     * @param expireSeconds       过期时长）
     * @param timeUnit    时间单位
     */
    public  <T> PageBean<T> queryWithLogicalExpirePage(String keyPrefix, String cacheKey,Class<T> clazz,
                                                       Function<Void, PageBean<T>> function,
                                                       Long expireSeconds,TimeUnit timeUnit){
        String key = keyPrefix + cacheKey;
        String lockKey = "pageLock:" + key;
        Long expireTime = timeUnit.toSeconds(expireSeconds);
        //空值过期时间1分钟
        long emptyExpireSeconds = 60;
        //查询Redies
        String json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(json)){
            //缓存命中
            return JSONUtil.toBean(json, new TypeReference<PageBean<T>>() {},false);
        }
        //空值判断
        if("".equals(json)){
            return null;
        }
        //未命中
        PageBean<T> pageBean = null;
        try {
            // 2. 自旋加锁
            boolean locked = tryLockWithRetry(lockKey, 5, 100);
            if (!locked) {
                log.warn("获取列表缓存锁失败，直接返回 null，key:{}", key);
                return null;
            }

            // 3. Double Check
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, new TypeReference<PageBean<T>>() {}, false);
            }
            if ("".equals(json)) {
                return null;
            }

            // 查数据库
            pageBean = function.apply(null);
            if (pageBean == null || pageBean.getItems().isEmpty()) {
                stringRedisTemplate.opsForValue().set(key, "", emptyExpireSeconds, TimeUnit.SECONDS);
                return pageBean;
            }
            // 写入缓存
            long randomSeconds = new Random().nextInt(3600);
            this.set(key, pageBean, expireTime + randomSeconds, TimeUnit.SECONDS);
        } finally {
            releaseLock(lockKey);
        }
        return pageBean;


    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR;
    static {
        CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
                10, // 核心线程数
                10, // 最大线程数
                60L, TimeUnit.SECONDS, // 空闲线程存活时间
                new ArrayBlockingQueue<>(100),
                new ThreadFactoryBuilder()
                        .setDaemon(true) // 守护线程，服务关闭自动退出
                        .build(),
                new ThreadPoolExecutor.DiscardPolicy()
        );
    }

    //上互斥锁
   public   boolean tryLock(String keyRedis){
        return stringRedisTemplate.opsForValue().setIfAbsent(keyRedis, "1", 10, TimeUnit.SECONDS);
    }
    //上互斥锁，重试机制
    public boolean tryLockWithRetry(String lockKey, int maxRetry, long retryIntervalMs) {
        for (int i = 0; i < maxRetry; i++) {
            if (tryLock(lockKey)) {
                return true;
            }
            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
    //释放互斥锁
    public void releaseLock(String keyRedis){
        stringRedisTemplate.delete(keyRedis);
    }


}
