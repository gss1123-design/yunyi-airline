package com.hakimi.aviation.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderVO {

    /**
     * 原样返回订单号（让前端校验一致性，防止串单）
     */
    private Long orderId;

    /**
     * 明确告知最新状态（比如 "CANCELLED"）
     * 前端拿到后可以直接将原本列表里的状态替换，无需重新查表
     */
    private String currentStatus = "CANCELLED";

    /**
     * 核心凭证：服务器执行取消的精确时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime cancelTime;

    /**
     * UX (用户体验) 杀手锏：退款金额 / 扣费金额
     * 虽然是未支付订单取消，但明明白白地告诉用户：扣费 0 元！
     */
    private BigDecimal penaltyFee = BigDecimal.ZERO;

    /**
     * 友好的业务提示
     */
    private String displayMessage = "订单已免费取消，期待您下次预订";
}
