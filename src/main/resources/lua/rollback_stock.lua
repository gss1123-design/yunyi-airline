-- ==========================================
-- 哈航核心链路：超时/退票 回滚与座位释放原子脚本
-- ==========================================

-- 接收 Java 传进来的 Key
local routeKey = KEYS[1]
local notFinishKey = KEYS[2]
local unpaidKey = KEYS[3]

-- 接收 Java 传进来的参数
local orderId = ARGV[1]
local flightId = ARGV[2]
local num = tonumber(ARGV[3])
-- 🚀 新增：座位偏移量 (seatOffset)
local seatOffset = tonumber(ARGV[4])

-- ==========================================
-- 🔄 动作 1：遍历航段，恢复库存与释放座位
-- ==========================================
local segmentIds = redis.call('lrange', routeKey, 0, -1)

if segmentIds and #segmentIds > 0 then
    for i, segId in ipairs(segmentIds) do
        -- 1. 恢复宏观库存
        local stockKey = 'stock:seg:' .. segId
        redis.call('incrby', stockKey, num)

        -- 2. 🚀 核心动作：释放微观座位 BitMap
        -- 只要 seatOffset 有效 (>=0)，就执行 SETBIT 还原为 0
        if seatOffset and seatOffset >= 0 then
            local bitmapKey = 'seatmap:seg:' .. segId
            redis.call('setbit', bitmapKey, seatOffset, 0)
        end
    end
end

-- ==========================================
-- 🧹 动作 2：打扫战场，释放用户权限
-- ==========================================
-- 从“行程防重 Set”中移除该航班，让用户可以重新购买
redis.call('srem', notFinishKey, flightId)

-- 从“未支付 Set”中移除该订单,如果没有传入 unpaidKey 说明此次业务无需删除未支付的记录
if unpaidKey then

    redis.call('srem', unpaidKey, orderId)

end


return 1