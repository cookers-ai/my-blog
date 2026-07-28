package com.itheima.big_event.utils;

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeout 超时时间
     * @return 是否获取到锁
     */
    boolean tryLock(Long timeout);
    /**
     * 释放锁
     */
    void unlock();
}
