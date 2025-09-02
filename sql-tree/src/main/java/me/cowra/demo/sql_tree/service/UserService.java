package me.cowra.demo.sql_tree.service;

import lombok.extern.slf4j.Slf4j;
import me.cowra.demo.sql_tree.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    public List<Map<String, Object>> getAllUsers() {
        log.info("get list of all users");
        return userMapper.findAll();
    }

}
