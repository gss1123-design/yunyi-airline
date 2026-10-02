package com.hakimi.aviation.service.user;

import com.hakimi.aviation.model.request.user.LoginRequest;
import com.hakimi.aviation.model.request.user.RegisterRequest;
import com.hakimi.aviation.model.request.user.SendCodeRequest;

public interface UserService {

    /**
     * 发送邮箱验证码的方法
     * @param request DTO 包含邮箱号
     * @return 会话令牌，注册时需回传以校验邮箱一致性
     */
    String sendVerifyCode(SendCodeRequest request);

    int register(RegisterRequest request);

    String login(LoginRequest request);

}
