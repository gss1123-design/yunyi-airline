package com.hakimi.aviation.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alibaba.fastjson.JSONObject;
import com.hakimi.aviation.entity.TicketOrder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AlipayProcess{

    @Resource
    private AlipayClient alipayClient;

    @Async
    public void triggerRefundProcess(TicketOrder ticketOrder) {

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        JSONObject bizContent = new JSONObject();
        // 必填：哈航的原始订单号，必须与支付时一致（"Hakimi-" 前缀），否则支付宝会报"交易不存在"
        bizContent.put("out_trade_no", "Hakimi-" + ticketOrder.getId());
        // 必填：退款金额（不能大于订单总金额），支付宝要求字符串格式
        bizContent.put("refund_amount", ticketOrder.getTotalPrice().toString());
        // 选填但强烈建议填：退款原因
        bizContent.put("refund_reason", "订单超时，座位已释放，自动退款");

        // 🚀 核心防御：退款防重幂等号！
        // 只要这个单号不变，支付宝就会认为是同一次退款请求，绝不会多退钱！
        String outRequestNo = "REFUND_" + ticketOrder.getId();
        bizContent.put("out_request_no", outRequestNo);

        request.setBizContent(bizContent.toString());

        try {
            // 2. 发起同步网络调用
            AlipayTradeRefundResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                // 3. 退款成功！更新退款流水表状态为 SUCCESS
                log.info("支付宝自动退款成功！订单号: {}, 退款金额: {}", ticketOrder.getId(), ticketOrder.getTotalPrice());
                // TODO: 发送消息给用户 （发布订阅模型）

            } else {
                // 🚨 业务报错（比如余额不足、订单状态不对）
                log.error("支付宝退款业务失败！订单号: {}, 错误码: {}, 原因: {}",
                        ticketOrder.getId(), response.getSubCode(), response.getSubMsg());
                // TODO: update sys_refund_log set status = 'FAIL', 发送飞书/钉钉报警给财务人工介入
            }
        } catch (Exception e) {
            // 🚨 网络异常/支付宝宕机
            log.error("调用支付宝退款接口发生网络异常！订单号: {}", ticketOrder.getId(), e);
            // TODO: update sys_refund_log set status = 'ERROR'，依赖定时任务或者人工重试
        }
    }

}


