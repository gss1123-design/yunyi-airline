package com.hakimi.aviation.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundVO {

    /**
     * 订单主键，方便前端后续拿着这个ID轮询查询最新退款状态
     */
    private Long orderId;

    /**
     * 预计退还金额（让前端展示，给用户吃一颗定心丸）
     */
    private BigDecimal expectedRefundAmount;

    /**
     * 订单当前状态（明确告知已进入退款流程）
     */
    private String status;

    /**
     * 友好的提示文案（直接让前端弹窗或者Toast展示）
     */
    private String promptMessage;
}