package com.hakimi.aviation.model.request.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    private String email;

    private String userName;
    /**
     * 密码已经过 MD5 加密
     */
    private String password;
    //随机六位数邮箱验证码
    private String verifyCode;
    //send_code 返回的会话令牌，用于校验注册邮箱与验证码接收邮箱一致
    private String sessionToken;

}
