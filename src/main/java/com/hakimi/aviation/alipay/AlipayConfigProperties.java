package com.hakimi.aviation.alipay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alipay") // 重点：告诉 Spring 去 YAML 里找 alipay 开头的配置
public class AlipayConfigProperties {
    private String appId;
    private String appPrivateKey;
    private String alipayPublicKey;
    private String notifyUrl;
    private String returnUrl;
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private String charset = "UTF-8";
    private String signType = "RSA2";
    private String format = "json";
}
