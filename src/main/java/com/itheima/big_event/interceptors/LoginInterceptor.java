package com.itheima.big_event.interceptors;

import com.itheima.big_event.pojo.Result;
import com.itheima.big_event.utils.JwtUtil;
import com.itheima.big_event.utils.RedisConstants;
import com.itheima.big_event.utils.ThreadLocalUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/*
* 登录拦截器
* */
@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //令牌验证
        //HttpServletRequest自带获取请求头中的token字段
        String token = request.getHeader("Authorization");
        //查询redis中是否有该token
       String redisKey=RedisConstants.LONG_USER_KEY+token;
       String userId=stringRedisTemplate.opsForValue().get(redisKey);
       if(userId==null){
           response.setStatus(401);
           return false;
       }
        //解析token
        //同时把业务数据放到ThreadLocal中，方便后续使用
        try {
            //利用try catch捕获异常，判断token是否有效有异常则返回未登录响应
            Map<String,Object> Claims= JwtUtil.parseToken(token);
            //把业务数据放到ThreadLocal中
            ThreadLocalUtil.set(Claims);
            //放行
           return true;
        } catch (Exception e) {
            //如果捕获到异常，说明token无效
            //http响应码变为401
            response.setStatus(401);
            //返回未登录响应
          return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清空ThreadLocal中的业务数据
        ThreadLocalUtil.remove();
    }
}
