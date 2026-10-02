package com.hakimi.aviation.message.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    // ======== 基础身份信息 ========
    private Long orderId;
    private Long userId;

    // ======== 支付宝网关所需 ========
    /** 支付时生成的商户订单号 */
    private String payTradeNo;
    /** 退款金额
     *  需要结合 时间等 决定是否扣减手续费 这里的金额是实际退还的，不一定是全款
     */
    private BigDecimal refundAmount;
    /** 退款请求幂等号（生成规则：REFUND_ + outTradeNo） */
    private String outRequestNo;

    // ======== 极限性能透传：Redis 与 DB 回滚所需 ========
    /** 航班ID */
    private Long flightId;
    /** 票数 */
    private Integer ticketCount;
    /** 座位号 */
    private Integer seatOffset;
}
