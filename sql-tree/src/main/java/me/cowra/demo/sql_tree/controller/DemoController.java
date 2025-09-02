package me.cowra.demo.sql_tree.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.cowra.demo.sql_tree.service.OrderService;
import me.cowra.demo.sql_tree.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
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


}
