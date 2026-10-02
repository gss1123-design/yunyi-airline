package com.hakimi.aviation.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AlipayCallbackUtil {

    @Resource
    private AlipayConfigProperties properties;

    /**
     * 解析请求参数并进行 RSA2 验签
     * @return 验签成功返回参数 Map，验签失败返回 null
     */
    public Map<String, String> verifyAndGetParams(HttpServletRequest request) {

        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();

        // 1. 把恶心的 HttpServletRequest 转换成干净的 Map
        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }

        // 2. 调用支付宝 SDK 进行 RSA2 验签
        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    properties.getAlipayPublicKey(),
                    properties.getCharset(),
                    properties.getSignType()
            );

            if (signVerified) {
                return params; // 验签通过，交出数据
            } else {
                log.error("支付宝回调验签失败！参数：{}", params);
                return null;   // 验签失败，拦截！
            }
        } catch (AlipayApiException e) {
            log.error("支付宝验签发生异常", e);
            return null;
        }
    }
}
