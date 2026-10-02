package com.hakimi.aviation.enums;

import lombok.Getter;
import lombok.Setter;

/**
 * 10以下的为通用码
 * 201-299 为用户业务故障码
 * 301-399 航班板块业务故障码
 * 401-499 B端业务故障码
 */
public enum BizCodeEnum {
    SUCCESS(0, "操作成功"),
    SERVICE_BUSY(1,"服务繁忙，请稍后再试"),
    FREQUENT_SEND_VERIFY(2,"验证码发送过于频繁"),
    OP_FREQUENT(3,"操作频繁，请稍后重试"),
    ILLEGAL_REQUEST(4,"非法请求，缺少必需属性"),
    EMPTY_PIC_CODE(201,"图形验证码不能为空"),
    PIC_CODE_ERROR(202,"图形验证码错误或已过期"),
    EMPTY_VERIFY_CODE(203,"邮箱验证码不能为空"),
    VERIFY_CODE_ERROR(204,"邮箱验证码错误或已过期"),
    MISSING_INFO(205,"缺少必要的注册信息"),
    VERIFY_SESSION_EXPIRED(206,"验证会话已过期，请重新获取验证码"),
    EMAIL_MISMATCH(207,"验证码对应的邮箱与当前注册邮箱不一致，请使用接收验证码的邮箱"),
    LOGIN_FAILED(208,"邮箱或密码错误"),
    REPEAT_PURCHASE(301,"已购买过此航班"),
    TICKET_SOLD_OUT(302,"机票已售罄"),
    FLIGHT_ERROR(303,"航班已下架或数据未就绪，请稍后重试"),
    ORDER_MISS_OR_EXPIRED(304,"您的订单不存在或已经超时"),
    ORDER_REFUND_FAILED(305,"退款失败，可能是重复请求或无效的退款请求，请联系客服"),
    ORDER_CAN_NOT_REFUND(306,"当前订单不允许退款：状态异常"),
    FLIGHT_INFO_MISS_REFUND_FAILED(307,"航班信息异常，退款失败"),
    REFUND_INTERNAL_FAILED(308,"第三方网关退款失败，请联系客服"),
    FINAL_REFUND_UPDATE_FAILED(309,"订单状态更新失败，请查看订单退款情况，如有异常请联系客服"),
    ILLEGAL_SEGMENT_SET(401,"航段配置非法");


    @Setter
    @Getter
    private int code;

    @Setter
    @Getter
    private String msg;

    BizCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

}
