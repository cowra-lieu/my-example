package me.cowra.demo.sql_tree.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.cowra.demo.sql_tree.mapper.OrderMapper;
import me.cowra.demo.sql_tree.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;

    public List<Map<String, Object>> getAllUsers() {
        log.info("get list of all users");
        return userMapper.findAll();
    }

    /**
     * 获取用户详细信息(包含订单)
     * 这个方法会产生多层SQL调用，用于演示调用树
     * @param userId 用户ID
     * @return 用户详细信息
     */
    public Map<String, Object> getUserDetailWithOrders(Long userId) {
        log.info("Get User Detail with Orders: id={}", userId);
        //* 1st level: get basic info of the user
        Map<String, Object> user = userMapper.findById(userId);
        if (user == null)
            return null;

        //* 2nd level: get all orders of the user
        Map<String, Object> orders = orderService.getUserOrders(userId);
        user.put("orders", orders);

        //* 3rd level: get statistics of the user
        Map<String, Object> userStats = userMapper.getUserStatistics(userId);
        user.put("statistics", userStats);

        return user;
    }
}
