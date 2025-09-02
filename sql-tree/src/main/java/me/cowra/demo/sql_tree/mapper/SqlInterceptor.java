package me.cowra.demo.sql_tree.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Properties;

/**
 * MyBatis SQL 拦截器
 * 如果存在多个拦截器会形成一个代理链.
 * 拦截器的执行顺序与注册顺序一致, 在 Spring 中可通过 @order 注解控制.
 */
@Slf4j
@RequiredArgsConstructor
//! 是一个 Spring 管理的 Bean
@Component
@Intercepts({
        //! 定义精确的拦截目标，包括拦截的接口、方法及参数类型，以区分重载方法
        //! type：指定要拦截的 MyBatis 核心组件接口。MyBatis 允许拦截以下四个接口:
        //! Executor: MyBatis 的内部执行器，负责整个 SQL 执行流程（如 update, query, commit, rollback等）。
        //! StatementHandler: 处理 SQL 语句的编译、参数设置和执行。
        //! ParameterHandler: 负责将用户传入的参数设置到 JDBC PreparedStatement中。
        //! ResultSetHandler: 处理查询结果集，将其映射到指定的 Java 对象。
        //* 当 MyBatis 执行任何查询操作（如 select）时
        //* 非常适合进行全局的 SQL 性能监控（记录执行时间）、缓存处理或查询结果集的后期处理
        @Signature(type= Executor.class, method = "query", args = {
                MappedStatement.class,
                Object.class,
                RowBounds.class,
                ResultHandler.class
        }),
        //* 当 MyBatis 执行任何写操作（如 insert, update, delete）时
        @Signature(type= Executor.class, method = "update", args = {
                MappedStatement.class,
                Object.class
        }),
        //! 在 SQL 语句被编译（准备）之后、真正执行之前。此时已经完成了 SQL 和参数的绑定，你可以获取到最终的 SQL 字符串.
        //* 用途：这是修改或重写 SQL 语句的理想时机。常见应用包括：
        //* 1. 自动分页：为 SQL 拼接 LIMIT和 OFFSET等分页子句。
        //* 2. 多租户数据隔离：自动在所有查询的 WHERE 条件中添加 tenant_id = xxx条件。
        //* 3. SQL 日志：打印完整可执行的 SQL 语句（虽然通常更推荐在 Executor层面做监控）
        @Signature(type = StatementHandler.class, method = "prepare", args = {
                Connection.class,
                Integer.class
        })
})
public class SqlInterceptor implements Interceptor {

    private final SqlCallTreeContext sqlCallTreeContext;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //* 检查是否启用追踪
        if (!sqlCallTreeContext.isTraceEnabled()) {
            return invocation.proceed();
        }

        Object target = invocation.getTarget();
        if (target instanceof Executor) {
            return interceptExecutor(invocation);
        } else if (target instanceof StatementHandler) {
            return interceptStatementHandler(invocation);
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        //* 只拦截 Executor 和 StatementHandler
        if (target instanceof Executor || target instanceof StatementHandler) {
            //! MyBatis 使用 Plugin类（它实现了 InvocationHandler）来创建代理。
            //! 即使用 JDK 动态代理创建代理对象.
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        if (properties != null) {
            String slowSqlThreshold = properties.getProperty("slowSqlThreshold");
            if (slowSqlThreshold != null) {
                long threshold = Long.parseLong(slowSqlThreshold);
                if (sqlCallTreeContext != null) {
                    sqlCallTreeContext.setSlowSqlThreshold(threshold);
                }
                log.info("Set threshold for slow query: {}ms", threshold);
            }

            String traceEnabled = properties.getProperty("traceEnabled");
            if (traceEnabled != null) {
                boolean enabled = Boolean.parseBoolean(traceEnabled);
                if (sqlCallTreeContext != null) {
                    sqlCallTreeContext.setTraceEnabled(enabled);
                }
                log.info("Set SQL trace: {}", enabled ? "Enabled" : "Disabled");
            }
        }
    }

    /**
     * 拦截 StatementHandler 准备
     */
    private Object interceptStatementHandler(Invocation invocation) throws Throwable {
        try {
            StatementHandler statementHandler = getStatementHandler(invocation);
            if (statementHandler != null) {
                BoundSql boundSql = statementHandler.getBoundSql();
                String sql = boundSql.getSql();

                log.debug("StatementHandler prepared SQL: {}", sql);
            }
        } catch (Exception e) {
            log.warn("Failed to intercept StatementHandler", e);
        }

        return invocation.proceed();
    }

    /**
     * 需要找到最终的 StatementHandler，通常是为了调用其 getBoundSql()等方法来获取 SQL 信息.
     * 如果操作的是代理对象，某些方法调用可能会再次触发拦截器逻辑，导致不必要的递归或意外行为.
     */
    private StatementHandler getStatementHandler(Invocation invocation) {
        try {
            Object target = invocation.getTarget();
            if (target instanceof StatementHandler) {
                return (StatementHandler) target;
            }

            //* 处理JDK动态代理对象
            MetaObject metaObject = SystemMetaObject.forObject(target);
            //* JDK 代理对象内部通常会有一个 h字段指向被代理的原始对象或者上一层代理
            while (metaObject.hasGetter("h")) {
                Object object = metaObject.getValue("h");
                if (object instanceof StatementHandler) {
                    return (StatementHandler) object;
                }
                metaObject = SystemMetaObject.forObject(object);
            }

            //* 处理 MyBatis 装饰器对象
            //* MyBatis 自身的 RoutingStatementHandler会包装具体的 StatementHandler实现（如 PreparedStatementHandler）
            while (metaObject.hasGetter("target")) {
                Object object = metaObject.getValue("target");
                if (object instanceof StatementHandler) {
                    return (StatementHandler) object;
                }
                metaObject = SystemMetaObject.forObject(object);
            }

        } catch (Exception e) {
            log.warn("Failed to get StatementHandler Instance", e);
        }

        return null;
    }

    /**
     * 拦截 Executor 执行
     */
    private Object interceptExecutor(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement mappedStatement = (MappedStatement) args[0];
        Object parameter = args[1];

        // 获取SQL信息
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        String sql = boundSql.getSql();
        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();


    }


}
