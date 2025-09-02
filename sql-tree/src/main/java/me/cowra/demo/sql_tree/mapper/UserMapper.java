package me.cowra.demo.sql_tree.mapper;

import org.apache.ibatis.annotations.Mapper;
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


}
