package me.cowra.demo.sql_tree.mapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class SqlCallTreeContext {

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
