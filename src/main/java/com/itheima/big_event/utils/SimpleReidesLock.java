package com.itheima.big_event.utils;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleReidesLock implements ILock {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private String keyName;
    private static final String LOCK_KEY_PREFIX="lock:";
    private static final String ID_SUFFIX= UUID.randomUUID().toString(true);
    //释放锁的lua脚本
    private static final DefaultRedisScript<Long> unlockScript;
    //初始化lua脚本
    static {
        unlockScript=new DefaultRedisScript<>();
        //设置lua脚本的位置
        unlockScript.setLocation(new ClassPathResource("unlock.Lua"));
        unlockScript.setResultType(Long.class);
    }
    public SimpleReidesLock(String keyName) {
        this.keyName = keyName;
    }
    @Override
    public boolean tryLock(Long timeout) {
        //获取线程id
        String threadId=ID_SUFFIX+Thread.currentThread().getId();
        //尝试获取锁，如果获取到锁，返回true，否则返回false
        boolean success=stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX+keyName,
                threadId+"",timeout, TimeUnit.SECONDS);
        //调用方法判断是否获取到锁（防止锁内的值为空造成空指针异常）
        return Boolean.TRUE.equals(success);
    }
    /*
     * Collections.singletonList() 方法：将单个元素转换为列表
     */
    @Override
    public void unlock() {
        //调用lua脚本，判断是否获取到锁
        Long result=stringRedisTemplate.execute(unlockScript,
                //锁的key
                Collections.singletonList(LOCK_KEY_PREFIX+keyName),
                //当前线程标识
                Collections.singletonList(ID_SUFFIX+Thread.currentThread().getId()));


    }
}
