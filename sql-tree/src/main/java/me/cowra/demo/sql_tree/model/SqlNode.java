package me.cowra.demo.sql_tree.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQL调用节点数据模型
 * 用于构建SQL调用树的基本数据结构
 */
@Data
public class SqlNode {

    private String nodeId;

    private String sql;

    private String formattedSql;

    /**
     * SQL类型(SELECT, INSERT, UPDATE, DELETE)
     */
    private String sqlType;

    private int depth; //* 调用深度

    private String threadName;
    private String serviceName; //* service 类名
    private String methodName;  //* service 方法名
    private String serviceCallPath; //* service 调用路径

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime endTime;

    //* 执行耗时
    private long executionTime;

    private boolean slowSql;

    private int affectedRows;

    private String errorMessage;

    private List<Object> parameters;    //* SQL参数


    private List<SqlNode> children; //* 子节点列表
    private String parentId;    //* 父节点ID

    /**
     * 构造函数
     */
    public SqlNode() {
        this.nodeId = UUID.randomUUID().toString();
        this.children = new ArrayList<>();
        this.parameters = new ArrayList<>();
        this.startTime = LocalDateTime.now();
        this.threadName = Thread.currentThread().getName();
    }

    /**
     * 构造函数
     * @param sql SQL语句
     * @param sqlType SQL类型
     * @param depth 调用深度
     */
    public SqlNode(String sql, String sqlType, int depth) {
        this();
        this.sql = sql;
        this.sqlType = sqlType;
        this.depth = depth;
        this.formattedSql = formatSql(sql);
    }

    public void addChild(SqlNode child) {
        if (child != null) {
            child.setParentId(this.nodeId);
            this.children.add(child);
        }
    }

    public List<SqlNode> getChildren() {
        return this.children;
    }

    public void setEndTime() {
        this.endTime = LocalDateTime.now();
        this.executionTime = calculateExecutionTime();
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        this.executionTime = calculateExecutionTime();
    }

    private long calculateExecutionTime() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }

    /**
     * 格式化SQL语句
     * @param sql 原始SQL
     * @return 格式化后的SQL
     */
    private String formatSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        //! 下面的正则表达式很容易匹配到字段值的部分,所以,只能作为简单的SQL格式化,
        return sql.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\s*(=|>|<|>=|<=|!=)\\s*", " $1 ")
                .replaceAll("\\s+(AND|OR|WHERE|FROM|JOIN|LEFT|RIGHT|INNER|OUTER|ON|GROUP|ORDER|HAVING|LIMIT)\\s+", " $1 ")
                .replaceAll("\\s+(BY|ASC|DESC)\\s+", " $1 ");
    }

    /**
     * 判断是否为慢SQL
     * @param threshold 慢SQL阈值(毫秒)
     * @return 是否为慢SQL
     */
    public boolean isSlowSql(long threshold) {
        return this.executionTime > threshold;
    }

    /**
     * 设置慢SQL标记
     * @param threshold 慢SQL阈值(毫秒)
     */
    public void markSlowSql(long threshold) {
        this.slowSql = isSlowSql(threshold);
    }

    /**
     * 获取慢SQL标记（用于JSON序列化）
     * @return 是否为慢SQL
     */
    public boolean isSlowSql() {
        return this.slowSql;
    }

    /**
     * 递归获取节点总数(包括子节点)
     * @return 节点总数
     */
    public int getTotalNodeCount() {
        int count = 1; // 当前节点
        for (SqlNode child : children) {
            count += child.getTotalNodeCount();
        }
        return count;
    }

    /**
     * 递归获取最大深度
     * @return 最大深度
     */
    public int getMaxDepth() {
        int maxDepth = this.depth;
        for (SqlNode child : children) {
            maxDepth = Math.max(maxDepth, child.getMaxDepth());
        }
        return maxDepth;
    }

    /**
     * 递归获取慢SQL节点数量
     * @return 慢SQL节点数量
     */
    public int getSlowSqlCount() {
        int count = this.slowSql ? 1 : 0;
        for (SqlNode child : children) {
            count += child.getSlowSqlCount();
        }
        return count;
    }

    /**
     * 递归获取总执行时间(包括子节点)
     * @return 总执行时间(毫秒)
     */
    public long getTotalExecutionTime() {
        long totalTime = this.executionTime;
        for (SqlNode child : children) {
            totalTime += child.getTotalExecutionTime();
        }
        return totalTime;
    }
}


