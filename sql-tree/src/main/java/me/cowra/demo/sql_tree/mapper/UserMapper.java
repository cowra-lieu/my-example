package me.cowra.demo.sql_tree.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface UserMapper {

    /**
     * Query all users
     * @return list of map of user's properties
     */
    @Select("""
            SELECT id, username, email, status, created_time, updated_time FROM
            users ORDER BY created_time DESC
            """)
    List<Map<String, Object>> findAll();


    @Select("SELECT * FROM users WHERE id = #{id}")
    Map<String, Object> findById(@Param("id") Long userId);

    //! 函数 COALESCE(v1, v2, ...) 从左到右寻找第一个非NULL值返回, 如果参数都是 NULL 它也就返回 NULL
    @Select("""
            SELECT
            COUNT(o.id) as order_count,
            COALESCE(SUM(o.total_amount), 0) as total_amount,
            COALESCE(AVG(o.total_amount), 0) as avg_amount,
            COUNT(CASE WHEN o.status = 'COMPLETED' THEN 1 END) as completed_orders,
            COUNT(CASE WHEN o.status = 'PENDING' THEN 1 END) as pending_orders
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            WHERE u.id = #{userId}
            GROUP BY u.id
            """)
    Map<String, Object> getUserStatistics(@Param("userId") Long userId);
}
