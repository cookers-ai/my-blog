package com.miniblog.aspect;

import com.miniblog.exception.PermissionException;
import com.miniblog.utils.ThreadLocalUtil;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
public class RequireAdminAspect {

    @Before("@annotation(com.miniblog.anno.RequireAdmin) || @within(com.miniblog.anno.RequireAdmin)")
    public void checkAdmin() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        String role = (String) claims.get("role");
        if (!"admin".equals(role)) {
            throw new PermissionException("只有管理员才能执行此操作");
        }
    }
}