package me.cowra.demo.sql_tree.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface OrderMapper {

    @Select("SELECT * FROM orders WHERE user_id = #{userId} ORDER BY created_time DESC")
    List<Map<String, Object>> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM order_items WHERE order_id = #{orderId} ORDER BY id")
    List<Map<String, Object>> findOrderItemsByOrderId(@Param("orderId") Long orderId);

    @Select("""
            SELECT
            COUNT(*) as item_count,
            SUM(quantity) as total_quantity,
            SUM(quantity * price) as calculated_total,
            AVG(price) as avg_price,
            MIN(price) as min_price,
            MAX(price) as max_price
            FROM order_items
            WHERE order_id = #{orderId}
            """)
    Map<String, Object> getOrderStatistics(@Param("orderId") Long orderId);
}
