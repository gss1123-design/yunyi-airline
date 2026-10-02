package com.hakimi.aviation.model.request.order;

import lombok.Data;

@Data
public class CancelOrderRequest {

    // 唯一标识 订单主键 对应数据库的id字段
    private Long orderId;

    //TODO 更多验证字段

}
