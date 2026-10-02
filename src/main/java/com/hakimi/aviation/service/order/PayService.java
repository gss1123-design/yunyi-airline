package com.hakimi.aviation.service.order;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface PayService {


    String payOrder(Long orderId);

    boolean confirmOrder(Long orderId,String tradeNo,String totalAmount);

}
