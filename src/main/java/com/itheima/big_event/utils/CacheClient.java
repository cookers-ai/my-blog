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

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpie(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData),
                time * 3, timeUnit);
    }

    public <T, ID> T articleDetailMutexLock(String keyPrefix, ID id, Class<T> clazz, Function<ID, T> function,
                                             Long time, TimeUnit timeUnit) {
        String keyRedis = keyPrefix + id;
        long expireTime = time;
        long emptyExpireTime = 60;

        String json = stringRedisTemplate.opsForValue().get(keyRedis);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, clazz);
        }
        if ("".equals(json)) {
            return null;
        }
        String lockKeyRedis = "Lock:" + keyPrefix + id;
        T t = null;

        if (!tryLock(lockKeyRedis)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("缓存重建被中断", e);
            }
            return articleDetailMutexLock(keyPrefix, id, clazz, function, time, timeUnit);
        }

        try {
            json = stringRedisTemplate.opsForValue().get(keyRedis);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, clazz);
            }
            if ("".equals(json)) {
                return null;
            }

            t = function.apply(id);
            if (t == null) {
                stringRedisTemplate.opsForValue().set(keyRedis, "", emptyExpireTime, TimeUnit.SECONDS);
                return null;
            }

            Random random = new Random();
            long expireSeconds = timeUnit.toSeconds(time);
            expireSeconds += random.nextInt(3600);
            this.set(keyRedis, t, expireSeconds, TimeUnit.SECONDS);
        } finally {
            releaseLock(lockKeyRedis);
        }

        return t;
    }

    public <T, ID> T queryWithLogicalExpire(
            String keyPrefix, ID id, Class<T> clazz,
            Function<ID, T> function, Long expireSeconds) {

        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            log.warn("逻辑过期缓存未命中，降级互斥锁查询，key: {}", key);
            return articleDetailMutexLock(keyPrefix, id, clazz, function, expireSeconds, TimeUnit.SECONDS);
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T data = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())) {
            return data;
        }

        String lockKey = "lock:" + key;
        if (tryLockWithRetry(lockKey, 3, 100)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    T newData = function.apply(id);
                    RedisData newRedisData = new RedisData();
                    newRedisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
                    newRedisData.setData(newData);
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

    public <T> PageBean<T> queryWithLogicalExpirePage(String keyPrefix, String cacheKey, Class<T> clazz,
                                                      Function<Void, PageBean<T>> function,
                                                      Long expireSeconds, TimeUnit timeUnit) {
        String key = keyPrefix + cacheKey;
        String lockKey = "pageLock:" + key;
        Long expireTime = timeUnit.toSeconds(expireSeconds);
        long emptyExpireSeconds = 60;

        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, new TypeReference<PageBean<T>>() {}, false);
        }
        if ("".equals(json)) {
            return null;
        }

        PageBean<T> pageBean = null;
        try {
            boolean locked = tryLockWithRetry(lockKey, 5, 100);
            if (!locked) {
                log.warn("获取列表缓存锁失败，直接返回 null，key:{}", key);
                return null;
            }

            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, new TypeReference<PageBean<T>>() {}, false);
            }
            if ("".equals(json)) {
                return null;
            }

            pageBean = function.apply(null);
            if (pageBean == null || pageBean.getItems().isEmpty()) {
                stringRedisTemplate.opsForValue().set(key, "", emptyExpireSeconds, TimeUnit.SECONDS);
                return pageBean;
            }

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
                10,
                10,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .build(),
                new ThreadPoolExecutor.DiscardPolicy()
        );
    }

    public boolean tryLock(String keyRedis) {
        return stringRedisTemplate.opsForValue().setIfAbsent(keyRedis, "1", 10, TimeUnit.SECONDS);
    }

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

    public void releaseLock(String keyRedis) {
        stringRedisTemplate.delete(keyRedis);
    }
}
