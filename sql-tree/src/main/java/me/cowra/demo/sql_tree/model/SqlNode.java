package me.cowra.demo.sql_tree.model;

import lombok.Data;

@Data
public class SqlNode {

    private String nodeId;

    private String sql;

    private String formattedSql;

    /**
     * SQL类型(SELECT, INSERT, UPDATE, DELETE)
     */
    private String sqlType;

}


