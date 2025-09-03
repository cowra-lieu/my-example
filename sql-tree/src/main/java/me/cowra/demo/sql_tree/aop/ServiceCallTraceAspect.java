package me.cowra.demo.sql_tree.aop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.cowra.demo.sql_tree.mapper.SqlCallTreeContext;
import me.cowra.demo.sql_tree.model.ServiceCallInfo;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Aspect
@Component
public class ServiceCallTraceAspect {

    private final SqlCallTreeContext sqlCallTreeContext;

    @Around("execution(public * me.cowra.demo.sql_tree.service.*Service.*(..))")
    public Object traceServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String serviceName = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        log.info("Service Aspect: {}.{}", serviceName, methodName);

        ServiceCallInfo serviceCallInfo = sqlCallTreeContext.enterService(serviceName, methodName);
        log.info("Entering service invocation: {}.{}, 当前深度: {}",
                serviceName, methodName,
                serviceCallInfo != null ? serviceCallInfo.getDepth() : "null");

        try {
            Object result = joinPoint.proceed();
            log.debug("Success Service Invocation: {}.{}", serviceName, methodName);
            return result;
        } catch (Throwable t) {
            log.error("Failed to execute service: {}.{}, error: {}",
                    serviceName, methodName, t.getMessage());
            throw t;
        } finally {
            sqlCallTreeContext.exitService(serviceCallInfo);
        }
    }

}
