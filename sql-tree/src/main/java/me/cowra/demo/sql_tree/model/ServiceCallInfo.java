package me.cowra.demo.sql_tree.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service调用信息
 * 用于追踪Service层的调用关系
 */
@Data
@Slf4j
public class ServiceCallInfo {

    private String callId;
    private String serviceName;
    private String methodName;
    private int depth;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long executionTime;

    private ServiceCallInfo parent;
    private List<ServiceCallInfo> children;

    /**
     * 该Service调用产生的SQL节点列表
     */
    private List<SqlNode> sqlNodes;

    /**
     * 构造函数
     */
    public ServiceCallInfo(String serviceName, String methodName, int depth) {
        this.callId = UUID.randomUUID().toString();
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.depth = depth;
        this.startTime = LocalDateTime.now();
        this.children = new ArrayList<>();
        this.sqlNodes = new ArrayList<>();
    }

    /**
     * 设置结束时间并计算执行时间
     */
    public void setEndTime() {
        this.endTime = LocalDateTime.now();
        this.executionTime = java.time.Duration.between(startTime, endTime).toMillis();
    }

    /**
     * 添加子Service调用
     */
    public void addChild(ServiceCallInfo child) {
        if (child != null) {
            child.setParent(this);
            this.children.add(child);
        }
    }

    /**
     * 添加SQL节点
     */
    public void addSqlNode(SqlNode sqlNode) {
        if (sqlNode != null) {
            this.sqlNodes.add(sqlNode);
        }
    }

    /**
     * 递归获取完整的Service调用路径
     */
    public String getFullCallPath() {
        if (parent == null) {
            return serviceName + "." + methodName;
        }
        return parent.getFullCallPath() + " -> " + serviceName + "." + methodName;
    }

    /**
     * 获取Service调用的简短描述
     */
    public String getShortDescription() {
        return String.format("%s.%s (depth=%d, time=%dms, SQLs=%d)",
                serviceName, methodName, depth, executionTime, sqlNodes.size());
    }

    /**
     * 判断是否为根Service调用
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * 获取该Service调用及其子调用产生的总SQL数量
     */
    public int getTotalSqlCount() {
        int count = sqlNodes.size();
        for (ServiceCallInfo child : children) {
            count += child.getTotalSqlCount();
        }
        return count;
    }

    /**
     * 获取该Service调用的最大深度
     */
    public int getMaxDepth() {
        int maxDepth = this.depth;
        for (ServiceCallInfo child : children) {
            maxDepth = Math.max(maxDepth, child.getMaxDepth());
        }
        return maxDepth;
    }

}
