local userBookedKey = KEYS[1]
local routeKey = KEYS[2]
local flightId = ARGV[1]
local salePauseKey = 'sale:pause:flight:' .. flightId

-- 运维确认恢复前始终快速拒绝该航班的新请求。
if redis.call('exists', salePauseKey) == 1 then
    return -5
end

-- 🟢 1. 缓存防丢校验 (返回 -4)
if redis.call('exists', userBookedKey) == 0 then
    return -4
end

-- 🟢 2. 查重校验 (返回 -3)
if redis.call('sismember', userBookedKey, flightId) == 1 then
    return -3
end

-- 🟢 3. 查底层航段 IDs (返回 -2)
local segmentIds = redis.call('lrange', routeKey, 0, -1)
if not segmentIds or #segmentIds == 0 then
    redis.call('set', salePauseKey, 'ROUTE_MISSING')
    return -2
end

-- 🟢 4. 预检宏观库存 (返回 -1)
for i, segId in ipairs(segmentIds) do
    local stockKey = 'stock:seg:' .. segId
    local stockValue = redis.call('get', stockKey)
    if not stockValue then
        redis.call('set', salePauseKey, 'STOCK_MISSING:' .. segId)
        return -2
    end

    if not string.match(stockValue, '^%d+$') then
        redis.call('set', salePauseKey, 'STOCK_INVALID:' .. segId)
        return -2
    end
    local currentStock = tonumber(stockValue)

    -- 库存为 0 是正常售罄；缺失或格式错误则由上方按数据故障停售。
    if currentStock < 1 then
        return -1
    end
end

-- 🟢 5. 核心逻辑：微观寻找“多航段公共空座”
local allocatedOffset = -1 -- 用来记录最终抢到的座位

-- 从 ARGV[2] 开始遍历我们的“优先探测数组”
for i = 2, #ARGV do
    local offset = tonumber(ARGV[i])
    local isCommonEmpty = true -- 假设当前偏移量在所有航段都是空座

    -- 遍历所有航段，核实这个偏移量
    for j, segId in ipairs(segmentIds) do
        local bitmapKey = 'seatmap:seg:' .. segId
        -- 如果发现只要有一个航段这个位置是 1 (有人)，就直接放弃这个座位
        if redis.call('getbit', bitmapKey, offset) == 1 then
            isCommonEmpty = false
            break -- 提前结束当前航段循环，看下一个偏移量
        end
    end

    -- 如果所有航段核实下来，这个座位真的都没人坐！
    if isCommonEmpty then
        allocatedOffset = offset -- 记录下这个天选之座
        break -- 退出探测循环，准备去扣减！
    end
end

-- 如果遍历了所有传入的座位，都没找到公共空座 (可能刚好被别人抢完)
if allocatedOffset == -1 then
    return -1
end

-- 🟢 6. 终极一击：原子化连环扣减与落库
for i, segId in ipairs(segmentIds) do
    local stockKey = 'stock:seg:' .. segId
    local bitmapKey = 'seatmap:seg:' .. segId

    redis.call('decr', stockKey) -- 库存 -1
    redis.call('setbit', bitmapKey, allocatedOffset, 1) -- 占座！
end

-- 🟢 7. 写入已购记录 (原子闭环)
redis.call('sadd', userBookedKey, flightId)

-- 🟢 8. 返回抢到的物理座位偏移量 (>= 0 代表成功)
return allocatedOffset
