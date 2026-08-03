package com.miniblog.interceptors;

import com.miniblog.utils.RedisConstants;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class TokenRefreshInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (StringUtils.isEmpty(token)) {
            return true;
        }
        String redisKey = RedisConstants.LONG_USER_KEY + token;
        Boolean exit = stringRedisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(exit)) {
            stringRedisTemplate.expire(redisKey, RedisConstants.TOKEN_EXPIRE_TIME, TimeUnit.MINUTES);
        }
        return true;
    }
}
