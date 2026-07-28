package com.itheima.big_event.config;

import com.itheima.big_event.interceptors.LoginInterceptor;
import com.itheima.big_event.interceptors.TokenRefreshInterceptor;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 配置类：配置拦截器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private TokenRefreshInterceptor tokenRefreshInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //添加登录拦截器
        //注册登录拦截器
        //.excludePathPatterns("/user/login","/user/register");指定排除的路径（放行）
        registry.addInterceptor(loginInterceptor).excludePathPatterns("/user/login","/user/register").order(1);
        //添加刷新刷新拦截器
        registry.addInterceptor(tokenRefreshInterceptor).addPathPatterns("/**").order(0);

    }
}
