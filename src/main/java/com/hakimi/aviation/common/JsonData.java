package com.hakimi.aviation.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JsonData<T> {

    /**
     * 业务码：200表示成功 其他表示异常，具体如下：
     *
     */
    private Integer code;

    //业务数据
    private T data;

    //提示信息
    private String msg;

    //时间戳
    private long timestamp;

    public static <T> JsonData<T> buildSuccess(T data,String msg){
        return new JsonData<>(200,data,msg,System.currentTimeMillis());
    }

    public static <T> JsonData<T> buildSuccess(String msg){
        return new JsonData<>(200,null,msg,System.currentTimeMillis());
    }

    public static <T> JsonData<T> buildError(Integer code,T data,String msg){
        return new JsonData<>(code,data,msg,System.currentTimeMillis());
    }

    public static <T> JsonData<T> buildError(String msg){
        return new JsonData<>(-1,null,msg,System.currentTimeMillis());
    }

}
