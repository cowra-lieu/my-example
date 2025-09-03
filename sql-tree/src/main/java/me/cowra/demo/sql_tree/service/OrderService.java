package me.cowra.demo.sql_tree.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.cowra.demo.sql_tree.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderService {

    private final OrderMapper orderMapper;

    public Map<String, Object> getUserOrders(Long userId) {
        log.info("Get orders of the user: id={}", userId);

        List<Map<String, Object>> orders = orderMapper.findByUserId(userId);
        //* 获取每个订单的详情
        for (Map<String, Object> order : orders) {
            Long orderId = (Long)order.get("id");
            List<Map<String,Object>> items = orderMapper.findOrderItemsByOrderId(orderId);
            order.put("items", items);

            Map<String, Object> stats = orderMapper.getOrderStatistics(orderId);
            order.put("statistics", stats);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("orders", orders);
        result.put("total", orders.size());
        return result;
    }
}
