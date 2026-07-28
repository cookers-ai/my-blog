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

    // @annotation: 用于指定在方法执行前执行的代码
    @Before("@annotation(com.itheima.big_event.anno.RequireAdmin) || @within(com.itheima.big_event.anno.RequireAdmin)")
    public void checkAdmin() {
        // 从ThreadLocal取出用户信息
        Map<String, Object> claims = ThreadLocalUtil.get();
        String role = (String) claims.get("role");

        // 权限校验
        if (!"admin".equals(role)) {
            throw new PermissionException("只有管理员才能执行此操作");
        }
    }
}