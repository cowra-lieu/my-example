package me.cowra.demo.sql_tree.mapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.cowra.demo.sql_tree.model.ServiceCallInfo;
import me.cowra.demo.sql_tree.model.SqlNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class SqlCallTreeContext {

    /**
     * 全局统计信息
     */
    private final SqlTraceStatistics globalStatistics = new SqlTraceStatistics();

    /**
     * 线程局部存储 - 配置信息
     */
    private final ThreadLocal<SqlTraceConfig> traceConfig = new ThreadLocal<SqlTraceConfig>() {
        @Override
        protected SqlTraceConfig initialValue() {
            return new SqlTraceConfig();
        }
    };

    /**
     * 线程本地存储 - SQL调用栈
     */
    private final ThreadLocal<Stack<SqlNode>> callStack = new ThreadLocal<Stack<SqlNode>>() {
        @Override
        protected Stack<SqlNode> initialValue() {
            return new Stack<>();
        }
    };

    /**
     * 线程本地存储 - Service调用栈
     */
    private final ThreadLocal<Stack<ServiceCallInfo>> serviceCallStack = new ThreadLocal<Stack<ServiceCallInfo>>() {
        @Override
        protected Stack<ServiceCallInfo> initialValue() {
            return new Stack<>();
        }
    };

    /**
     * 线程本地存储 - 根节点列表
     */
    private final ThreadLocal<List<SqlNode>> rootNodes = new ThreadLocal<List<SqlNode>>() {
        @Override
        protected List<SqlNode> initialValue() {
            return new ArrayList<>();
        }
    };

    /**
     * 慢SQL阈值(毫秒)
     */
    private volatile long slowSqlThreshold;
    /**
     * 是否启用追踪
     */
    private volatile boolean traceEnabled;

    public void setSlowSqlThreshold(long threshold) {
        this.slowSqlThreshold = threshold;
        log.info("设置慢SQL阈值: {}ms", threshold);
    }

    public void setTraceEnabled(boolean enabled) {
        this.traceEnabled = enabled;
        log.info("设置SQL追踪状态: {}", enabled ? "启用" : "禁用");
    }

    /**
     * 检查是否启用追踪
     * @return 是否启用追踪
     */
    public boolean isTraceEnabled() {
        return traceEnabled && traceConfig.get().isEnabled();
    }

    public SqlNode enter(String sql, String sqlType) {
        if (!isTraceEnabled())
            return null;

        try {
            Stack<SqlNode> sqlNodeStack = callStack.get();
            Stack<ServiceCallInfo> serviceCallInfoStack = serviceCallStack.get();

            ServiceCallInfo currentServiceCall = serviceCallInfoStack.isEmpty() ? null : serviceCallInfoStack.peek();

            //* 计算 SQL 深度: 基于Service调用深度
            int sqlDepth;
            if (currentServiceCall != null) {
                //* 如果在 service 调用中, service深度就是sql深度
                sqlDepth = currentServiceCall.getDepth();
                log.info("SQL Depth: service={}, serviceDepth={}, sqlDepth={}",
                        currentServiceCall.getServiceName(), currentServiceCall.getDepth(), sqlDepth);
            } else {
                //* 如果不在 service 调用中, 使用传统的SQL栈深度
                sqlDepth = sqlNodeStack.size() + 1;
                log.info("SQL Depth: without service invocation, sqlStackSize={}, sqlDepth={}",
                        sqlNodeStack.size(), sqlDepth);
            }

            SqlNode node = new SqlNode(sql, sqlType, sqlDepth);
            //* 填充 service 调用信息
            if (currentServiceCall != null) {
                node.setServiceName(currentServiceCall.getServiceName());
                node.setMethodName(currentServiceCall.getMethodName());
                node.setServiceCallPath(currentServiceCall.getFullCallPath());
                currentServiceCall.addSqlNode(node);
            }

            //* 建立父子关系
            SqlNode parentSqlNode = findParentSqlNode(sqlNodeStack, serviceCallInfoStack);
            if (parentSqlNode != null) {
                parentSqlNode.addChild(node);
                log.info("Build parent-child relationship: parent[{}.{}] -> child[{}.{}], size of children={}",
                        parentSqlNode.getServiceName(), parentSqlNode.getMethodName(),
                        node.getServiceName(), node.getMethodName(),
                        parentSqlNode.getChildren().size());
            } else {
                //* 没有父节点,那么当前节点是根节点
                rootNodes.get().add(node);
                log.info("add root node: {}.{}", node.getServiceName(), node.getMethodName());
            }

            //* 新节点入栈
            sqlNodeStack.push(node);

            //* 更新统计信息
            globalStatistics.incrementTotalSqlCount();
            globalStatistics.updateMaxDepth(sqlDepth);

            log.debug("Enter SQL invocation: depth={}, service={}, sql={}",
                    sqlDepth,
                    currentServiceCall != null ? currentServiceCall.getShortDescription() : "none",
                    sql);

            return node;

        } catch (Exception e) {
            log.error("Failed to enter SQL invocation", e);
            return null;
        }

    }

    /**
     * 查找SQL节点的父节点
     * 基于Service调用关系确定SQL的父子关系
     */
    private SqlNode findParentSqlNode(Stack<SqlNode> sqlNodeStack, Stack<ServiceCallInfo> serviceCallInfoStack) {
        //* 如果 SqlNodeStack 非空,直接取栈顶节点作为父节点
        if (!sqlNodeStack.isEmpty()) {
            SqlNode parentSqlNode = sqlNodeStack.peek();
            log.info("Find parent node: SQL stack size={}, parent node={}.{}[depth={}]",
                    sqlNodeStack.size(),
                    parentSqlNode.getServiceName(), parentSqlNode.getMethodName(),
                    parentSqlNode.getDepth());
            return parentSqlNode;
        }

        //* 如果 sql 栈空, 但 service 栈非空, 查找父 service 的最后一个 SQL 节点
        if (!serviceCallInfoStack.isEmpty()) {
            ServiceCallInfo currentService = serviceCallInfoStack.peek();
            if (currentService.getParent() != null) {
                ServiceCallInfo parentService = currentService.getParent();
                List<SqlNode> parentSqlNodes = parentService.getSqlNodes();
                if (!parentSqlNodes.isEmpty()) {
                    SqlNode parentNode = parentSqlNodes.get(parentSqlNodes.size() - 1);
                    log.info("Cross Services to find parent sql node: parent service={}, parent node={}.{}[depth={}]",
                            parentService.getServiceName(),
                            parentNode.getServiceName(), parentNode.getMethodName(),
                            parentNode.getDepth());
                    return parentNode;
                }
            }
        }

        log.info("No parent sql node found");
        return null;
    }

    public void exit(SqlNode sqlNode, int affectedRows, String errorMessage) {
        if (!isTraceEnabled() || sqlNode == null)
            return;

        try {
            Stack<SqlNode> sqlNodeStack = callStack.get();
            if (!sqlNodeStack.isEmpty() && sqlNodeStack.peek().getNodeId().equals(sqlNode.getNodeId())) {
                //* 弹出 sql node 栈顶
                SqlNode currentNode = sqlNodeStack.pop();
                //* 设置结束时间
                currentNode.setEndTime();
                currentNode.setAffectedRows(affectedRows);
                currentNode.setErrorMessage(errorMessage);
                //* 标记慢SQL
                currentNode.markSlowSql(slowSqlThreshold);
                //* 更新统计信息
                if (currentNode.isSlowSql()) {
                    globalStatistics.incrementSlowSqlCount();
                }
                if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                    globalStatistics.incrementErrorSqlCount();
                }
                globalStatistics.addExecutionTime(currentNode.getExecutionTime());

                log.debug("SQL Invocation Exist: depth={}, executionTime={}ms, sql={}",
                        currentNode.getDepth(), currentNode.getExecutionTime(), currentNode.getSql());

                //* 不在 SQL调用退出时保存, 在 Service 退出时保存,确保 Service调用树完全构建后再保存
            }

        } catch (Exception e) {
            log.error("Failed to exit sql invocation", e);
        }
    }

    public ServiceCallInfo enterService(String serviceName, String methodName) {
        if (!isTraceEnabled())
            return null;

        try {
            Stack<ServiceCallInfo> stack = serviceCallStack.get();
            int depth = stack.size() + 1;

            ServiceCallInfo serviceCallInfo = new ServiceCallInfo(serviceName, methodName, depth);

            if (!stack.isEmpty()) {
                ServiceCallInfo parent = stack.peek();
                parent.addChild(serviceCallInfo);
            }

            stack.push(serviceCallInfo);

            log.debug("Entering service invocation: {}", serviceCallInfo.getShortDescription());
            return serviceCallInfo;

        } catch (Exception e) {
            log.error("Failed to enter service invocation", e);
            return null;
        }
    }

    public void exitService(ServiceCallInfo serviceCallInfo) {
        if (!isTraceEnabled() || serviceCallInfo == null)
            return;

        try {
            Stack<ServiceCallInfo> stack = serviceCallStack.get();

            if (!stack.isEmpty() && stack.peek().getCallId().equals(serviceCallInfo.getCallId())) {
                ServiceCallInfo currentCall = stack.pop();
                currentCall.setEndTime();
                log.debug("Exit service invocation: {}", currentCall.getShortDescription());
                //* 确保每个独立的 service 只保存一次
                if (currentCall.getDepth() == 1) {
                    saveToGlobalSession();
                    log.info("Complete top-level service invocation, Save call-tree to global session: {}", currentCall.getShortDescription());
                }
            } else {
                log.warn("Not matched Service Call Stack: expected={}, actual={}",
                        serviceCallInfo.getCallId(),
                        stack.isEmpty() ? "empty" : stack.peek().getCallId());
            }

        } catch (Exception e) {
            log.error("Failed to exit service invocation", e);
        }
    }

    /**
     * 全局会话存储 - 线程ID -> 根节点列表
     */
    private final Map<String, List<SqlNode>> globalSessions = new ConcurrentHashMap<>();

    private void saveToGlobalSession() {
    }

    @Data
    public static class SqlTraceConfig {
        private boolean enabled = true; //* 是否启用追踪
        private int maxDepth = 50;  //* 最大调用深度
        private long slowSqlThreshold = 1000L;  //* 慢SQL阈值(毫秒)
        private boolean recordParameters = true;    // * 是否记录SQL参数
        private int maxSessions = 100;  //* 最大会话数
    }

    /**
     * SQL追踪统计信息
     */
    @Data
    public static class SqlTraceStatistics {
        /**
         * 总SQL数量
         */
        private final AtomicLong totalSqlCount = new AtomicLong(0);

        /**
         * 慢SQL数量
         */
        private final AtomicLong slowSqlCount = new AtomicLong(0);

        /**
         * 错误SQL数量
         */
        private final AtomicLong errorSqlCount = new AtomicLong(0);

        /**
         * 总执行时间
         */
        private final AtomicLong totalExecutionTime = new AtomicLong(0);

        /**
         * 最大调用深度
         */
        private final AtomicInteger maxDepth = new AtomicInteger(0);

        /**
         * 统计开始时间
         */
        private final LocalDateTime startTime = LocalDateTime.now();

        public void incrementTotalSqlCount() {
            totalSqlCount.incrementAndGet();
        }

        public void incrementSlowSqlCount() {
            slowSqlCount.incrementAndGet();
        }

        public void incrementErrorSqlCount() {
            errorSqlCount.incrementAndGet();
        }

        public void addExecutionTime(long time) {
            totalExecutionTime.addAndGet(time);
        }

        public void updateMaxDepth(int depth) {
            maxDepth.updateAndGet(current -> Math.max(current, depth));
        }

        public long getTotalSqlCount() {
            return totalSqlCount.get();
        }

        public long getSlowSqlCount() {
            return slowSqlCount.get();
        }

        public long getErrorSqlCount() {
            return errorSqlCount.get();
        }

        public long getTotalExecutionTime() {
            return totalExecutionTime.get();
        }

        public int getMaxDepth() {
            return maxDepth.get();
        }

        public double getAverageExecutionTime() {
            long total = getTotalSqlCount();
            return total > 0 ? (double) getTotalExecutionTime() / total : 0.0;
        }

        public void reset() {
            totalSqlCount.set(0);
            slowSqlCount.set(0);
            errorSqlCount.set(0);
            totalExecutionTime.set(0);
            maxDepth.set(0);
        }

        public SqlTraceStatistics copy() {
            SqlTraceStatistics copy = new SqlTraceStatistics();
            copy.totalSqlCount.set(this.totalSqlCount.get());
            copy.slowSqlCount.set(this.slowSqlCount.get());
            copy.errorSqlCount.set(this.errorSqlCount.get());
            copy.totalExecutionTime.set(this.totalExecutionTime.get());
            copy.maxDepth.set(this.maxDepth.get());
            return copy;
        }
    }
}
