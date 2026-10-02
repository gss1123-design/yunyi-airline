package com.hakimi.aviation.message.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 此消息体用以实现超时未支付订单取消功能
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderMessage {

    //订单Id 主要用于检测与取消订单操作
    private Long OrderId;

    //航班 Id 和 用户 Id 都是用以定位 Redis 中的未支付记录 如果找不到未支付记录 此条消息会被丢弃
    private Long flightId;
    private Long userId;
    private Integer seatOffset;
}
