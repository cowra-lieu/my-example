package me.cowra.demo.sql_tree.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.cowra.demo.sql_tree.service.OrderService;
import me.cowra.demo.sql_tree.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api")
public class DemoController {

    private final UserService userService;
    private final OrderService orderService;

    /**
     * 获取所有用户信息
     */
    @GetMapping("/users")
    public Map<String, Object> getAllUsers() {
        log.info("get all users");
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> allUsers = userService.getAllUsers();
            response.put("success", true);
            response.put("data", allUsers);
            response.put("total", allUsers.size());
        } catch (Exception e) {
            log.error("Failed to retrieve the user list", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * 根据ID获取用户信息(包含订单信息)
     * 这个接口会产生复杂的接口调用树
     * @param id 用户ID
     * @return 用户详情
     */
    @GetMapping("/users/{id}")
    public Map<String, Object> getUserDetail(@PathVariable Long id) {
        log.info("获取用户详情: id={}", id);

        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> userDetail = userService.getUserDetailWithOrders(id);
            if (userDetail == null) {
                response.put("success", false);
                response.put("message", "No user found");
            } else {
                response.put("success", true);
                response.put("data", userDetail);
            }
        } catch (Exception e) {
            log.error("Failed to get user detail", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }


}
