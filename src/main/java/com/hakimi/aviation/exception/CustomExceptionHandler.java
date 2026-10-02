package com.hakimi.aviation.exception;

import com.hakimi.aviation.common.JsonData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CustomExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomExceptionHandler.class);

    @ExceptionHandler(value = BizException.class)
    public JsonData handle(BizException bizException){

        return JsonData.buildError(bizException.getCode(),null,bizException.getMsg());

    }

    @ExceptionHandler(value = Exception.class)
    public JsonData handle(Exception e) {
        // 必须记录日志，方便排查
        logger.error("[ 系统异常 ]", e);
        // 对外统一模糊提示，防止泄露底层数据库结构或代码逻辑
        return JsonData.buildError(-1,null,"未知异常，请联系管理员");
    }

}
