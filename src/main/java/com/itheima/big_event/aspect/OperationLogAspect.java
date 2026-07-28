package com.itheima.big_event.aspect;

import com.alibaba.fastjson.JSON;
import com.itheima.big_event.anno.Log;
import com.itheima.big_event.mapper.OperationLogMapper;
import com.itheima.big_event.pojo.OperationLog;
import com.itheima.big_event.utils.ThreadLocalUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

@Aspect
@Component
public class OperationLogAspect {
    @Autowired
    private OperationLogMapper operationLogMapper;

    @Around("@annotation(log)")
    public Object around(ProceedingJoinPoint joinPoint, Log log) throws Throwable {
        // 1. 获取基础信息
        OperationLog operationLog = new OperationLog();
        operationLog.setCreateTime(LocalDateTime.now());
        operationLog.setModule(log.module());
        operationLog.setOperation(log.operation());
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        operationLog.setMethod(className + "." + methodName);
        //  获取请求参数（
        Object[] args = joinPoint.getArgs();
        StringBuilder paramsBuilder = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
                continue;
            }
            paramsBuilder.append(com.alibaba.fastjson.JSON.toJSONString(arg)).append(";");
        }
        operationLog.setParams(paramsBuilder.toString());

        // 3. 获取当前登录用户信息
        try {
            Map<String, Object> claims = ThreadLocalUtil.get();
            if (claims != null) {
                //记录id
                operationLog.setUserId(Long.valueOf(claims.get("id").toString()));
                //记录用户名
                operationLog.setUsername((String) claims.get("username"));
            }
        } catch (Exception e) {
            // 用户未登录也可能触发带 @Log 的方法？一般不会，但防一下
            operationLog.setUserId(null);
            operationLog.setUsername("anonymous");
        }

        // 4. 获取操作人IP
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                operationLog.setIp(ip);
            }
        } catch (Exception e) {
            operationLog.setIp("unknown");
        }

        // 5. 执行目标方法，记录耗时和状态
        long start = System.currentTimeMillis();
        Object result;
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            operationLog.setStatus(1);
            operationLog.setErrorMsg(null);
        } catch (Throwable e) {
            operationLog.setStatus(0);
            operationLog.setErrorMsg(e.getMessage());
            throw e; // 异常要继续抛出，由全局异常处理器处理
        } finally {
            long costTime = System.currentTimeMillis() - start;
            operationLog.setCostTime(costTime);
            // 6. 保存日志（异步可优化，见下文）
            operationLogMapper.insert(operationLog);
        }
        return result;
    }
}
