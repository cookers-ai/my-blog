package com.miniblog.utils;

/**
 * ThreadLocal 工具类
 * 作用：在同一个线程中，安全地共享当前登录用户信息，避免每个接口都重复解析 Token
 */
public class ThreadLocalUtil {

    // 1. 创建一个静态常量的 ThreadLocal 对象
    // ThreadLocal 的特点：每个线程独有一份数据，线程之间互不干扰
    private static final ThreadLocal THREAD_LOCAL = new ThreadLocal();

    /**
     * 2. 从当前线程中获取存入的数据
     * @param <T> 泛型，适配不同类型的数据（比如用户ID、用户对象）
     * @return 存入的数据
     */
    public static <T> T get() {
        return (T) THREAD_LOCAL.get();
    }

    /**
     * 3. 向当前线程中存入数据
     * @param value 要存入的数据（比如解析 Token 得到的用户信息）
     */
    public static void set(Object value) {
        THREAD_LOCAL.set(value);
    }

    /**
     * 4. 清除当前线程中的数据
     * 【重点】请求结束后必须调用！防止内存泄漏
     */
    public static void remove() {
        THREAD_LOCAL.remove();
    }
}