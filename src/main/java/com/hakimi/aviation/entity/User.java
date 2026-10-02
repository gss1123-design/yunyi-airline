package com.hakimi.aviation.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {

    private Long id;

    //唯一索引，邮箱号
    private String email;

    private String userName;

    //MD5 加密
    private String password;

    //TODO 是否留用待定
    private String avatar;

    private Date registerTime;

    //用户账号是否注销：1 = alive
    @TableLogic
    private Integer isDeleted;
}
