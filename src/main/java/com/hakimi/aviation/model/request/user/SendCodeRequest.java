package com.hakimi.aviation.model.request.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendCodeRequest {

    private String email;

    //图形验证码
    private String picCode;

    //Redis 中存储图形验证码的key
    private String captchaKey;

}
