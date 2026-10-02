package com.hakimi.aviation.exception;

import com.hakimi.aviation.enums.BizCodeEnum;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BizException extends RuntimeException{

    private Integer code;

    private String msg;

    public BizException(BizCodeEnum bizCodeEnum){
        this.code = bizCodeEnum.getCode();
        this.msg = bizCodeEnum.getMsg();
    }

}
