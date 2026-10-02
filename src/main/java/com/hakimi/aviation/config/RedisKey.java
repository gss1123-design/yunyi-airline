package com.hakimi.aviation.config;

public class RedisKey {

    //机票库存的 BaseKey 需要拼接上航段实例的主键ID 用String结构存储
    public static final String STOCK_KEY = "stock:seg:";

    //航班路线的 BaseKey 需要拼接上航班的主键ID 用List结构存储
    public static final String ROUTE_FLIGHT = "route:flight:";

    //航班信息的 BaseKey 后面拼接上具体航班的主键ID 用Hash结构存储
    public static final String INFO_FLIGHT = "info:flight:";

    //航班故障时的停售屏障。由抢票 Lua 脚本设置，修复缓存并经 B 端确认后清除。
    public static final String FLIGHT_SALE_PAUSE_KEY = "sale:pause:flight:";

    //后面拼接用户 Id 存储用户未结束的行程 用以在预订时检查是否重复购买或行程冲突 只查询未结束的行程（已取消和已结束的不管）
    //在用户登录时发起 用Set结构存储
    public static final String ORDER_NOT_FINISH_KEY = "order:notFinish:";

    //后面拼接用户 Id 存储用户未支付的订单 用以做超时失效处理 只查询状态为未支付的订单 每次预订航班成功之后发起 TTL为超时限制时间
    //在消费者端发起 用Set结构存储
    public static final String ORDER_UNPAID_KEY = "order:unpaid:";

    //订单快照 用 Hash 结构存储 存放支付时所必需的数据
    //后面拼接上 订单ID
    public static final String ORDER_SNAPSHOT_KEY = "order:snapshot:";

    //航段实例所持有的座位的 BitMap 位图的 Key 后面需要拼接上 SegmentInstanceId
    public static final String SEGMENT_SEAT_BIT_MAP_KEY = "seatmap:seg:";

    //退款的分布式锁，key 的后面需要拼接上 此次退款订单的唯一主键 orderId
    public static final String REFUND_LOCK = "lock:refund:";

}
