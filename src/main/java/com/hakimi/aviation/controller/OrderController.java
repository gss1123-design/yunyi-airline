package com.hakimi.aviation.controller;

import com.hakimi.aviation.alipay.AlipayCallbackUtil;
import com.hakimi.aviation.common.JsonData;
import com.hakimi.aviation.model.request.order.CancelOrderRequest;
import com.hakimi.aviation.model.request.order.RefundRequest;
import com.hakimi.aviation.model.vo.CancelOrderVO;
import com.hakimi.aviation.model.vo.OrderRefundVO;
import com.hakimi.aviation.model.vo.OrderVO;
import com.hakimi.aviation.service.order.OrderService;
import com.hakimi.aviation.service.order.PayService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/pri/order")
public class OrderController {

    @Resource
    private PayService payService;

    @Resource
    private AlipayCallbackUtil alipayCallbackUtil;

    @Resource
    private OrderService orderService;


    /**
     * 真实业务调用的接口 此接口需要做访问限制 不能连续多次请求
     * @param orderId 订单号
     * @return 包装好的 支付界面 HTML
     */
    @PostMapping("pay")
    public JsonData<String> payOrder(@RequestParam("order_id") Long orderId){

        String formHtml = payService.payOrder(orderId);

        return JsonData.buildSuccess(formHtml,"已收到第三方支付平台响应");
    }

    /**
     * 测试用的接口 直接返回 HTML
     * @param orderId 订单号
     * @return 支付界面的 HTML 代码
     */
    @GetMapping("pay/test")
    public String testPayOrder(@RequestParam("order_id") Long orderId){

        String formHtml = payService.payOrder(orderId);

        return formHtml;
    }

    /**
     * 支付宝回调的接口 在这里接收支付宝回调的 HTTP 请求参数
     * @param request 支付宝回调的 HTTP 请求
     * @return ！！！此接口只允许支付宝调用 同时只能给支付宝返回结果！！！  返回 "success" 通知支付宝 支付成功结束; "failure" 则是失败
     */
    @PostMapping("pay/callback")
    public String alipayCallBack(HttpServletRequest request){

        log.info("接口已被支付宝回调");

        Map<String, String> params = alipayCallbackUtil.verifyAndGetParams(request);
        if (params == null) {
            return "failure";
        }

        log.info("请求通过基本校验");

        // 状态过滤：不是支付成功的回调，直接忽略
        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return "success";
        }

        log.info("请求通过支付状态校验");

        // 提取核心业务参数
        String outTradeNo = params.get("out_trade_no");     // Hakimi-2049...
        String tradeNo = params.get("trade_no");           // 支付宝流水号
        String totalAmount = params.get("total_amount");   // 支付金额

        // 切掉前缀，拿到纯净的雪花 ID
        Long orderId = Long.parseLong(outTradeNo.replace("Hakimi-", ""));


        boolean result = payService.confirmOrder(orderId, tradeNo, totalAmount);

        // 根据处理结果，给支付宝答复
        return result ? "success" : "failure";
    }

    /**
     * 用户取消订单的接口 必须保障幂等性  只能对未进行支付的接口进行取消
     * 此接口没有 @LoginOptional
     * @param request DTO 只携带 订单号
     * @param servletRequest HTTP 上下文
     * @return 是否操作成功
     */
    @PostMapping("cancel")
    public JsonData<CancelOrderVO> cancelOrder(@RequestBody CancelOrderRequest request, HttpServletRequest servletRequest){

        Long userId = (Long) servletRequest.getAttribute("user_id");

        CancelOrderVO cancelOrderVO = orderService.cancelOrder(request, userId);

        return JsonData.buildSuccess(cancelOrderVO,"操作成功");
    }

    /**
     * 用户进行退款的接口，只能对已经支付过的接口进行，需要调用三方接口进行退款
     * 退款时需要结合当前与起飞的时间，决定是否扣除手续费/扣除多少
     * @return 退款成功的通知，应另起一个 VO 类用作展示
     */
    @PostMapping("refund")
    public JsonData<OrderRefundVO> refundOrder(@RequestBody RefundRequest request, HttpServletRequest servletRequest){


        Long userId = (Long) servletRequest.getAttribute("user_id");

        //TODO 调用 service 方法，发送消息，最后返回一个友好的提示消息 包装 VO
        OrderRefundVO orderRefundVO = orderService.refundOrder(request, userId);

        return JsonData.buildSuccess(orderRefundVO,"操作成功");
    }

    /**
     * 我的订单列表 需登录 userId 从 JWT 取
     * @param status 可选 订单状态筛选（UNPAID/PAID/CANCELLED/REFUNDING/REFUNDED），不传则查全部
     * @return 订单列表（含航班展示信息与座位号），按下单时间倒序
     */
    @GetMapping("list")
    public JsonData<List<OrderVO>> listOrders(@RequestParam(value = "status", required = false) String status,
                                              HttpServletRequest servletRequest){

        Long userId = (Long) servletRequest.getAttribute("user_id");

        List<OrderVO> orders = orderService.listOrders(userId, status);

        return JsonData.buildSuccess(orders, "查询成功");
    }

    /**
     * 订单详情 需登录 且只能查本人订单 常用于支付/退款后轮询最新状态
     * @param orderId 订单主键ID
     * @return 单个订单详情
     */
    @GetMapping("detail")
    public JsonData<OrderVO> orderDetail(@RequestParam("order_id") Long orderId,
                                         HttpServletRequest servletRequest){

        Long userId = (Long) servletRequest.getAttribute("user_id");

        OrderVO order = orderService.getOrderDetail(orderId, userId);

        return JsonData.buildSuccess(order, "查询成功");
    }

}
