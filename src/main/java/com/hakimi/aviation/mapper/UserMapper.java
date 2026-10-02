package com.hakimi.aviation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hakimi.aviation.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserMapper extends BaseMapper<User> {

    int register(User user);

    User findUserById(@Param("user_id") Integer userId);

    User findUserByEmailAndPwd(@Param("email") String email, @Param("password") String password);

}
