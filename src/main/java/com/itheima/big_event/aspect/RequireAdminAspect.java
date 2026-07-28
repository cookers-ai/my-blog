package com.itheima.big_event.aspect;

import com.itheima.big_event.exception.PermissionException;
import com.itheima.big_event.utils.ThreadLocalUtil;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
public class RequireAdminAspect {

    @Before("@annotation(com.itheima.big_event.anno.RequireAdmin) || @within(com.itheima.big_event.anno.RequireAdmin)")
    public void checkAdmin() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        String role = (String) claims.get("role");
        if (!"admin".equals(role)) {
            throw new PermissionException("只有管理员才能执行此操作");
        }
    }
}