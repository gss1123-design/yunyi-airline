-- KEYS[1]: 航班拓扑 Key (例如 route:flight:101)
-- ARGV[1]: 购买数量 (通常为 1)
local routeKey = KEYS[1]
local num = tonumber(ARGV[1])

-- 1. 内部寻址：直接从 Redis List 中获取该航班的所有航段 ID
local segmentIds = redis.call('lrange', routeKey, 0, -1)

if not segmentIds or #segmentIds == 0 then
    return -2 -- 错误：找不到航段配置，可能是缓存未预热
end

-- 2. 预检阶段：检查所有航段库存是否足够
for i, segId in ipairs(segmentIds) do
    local stockKey = 'stock:seg:' .. segId
    local currentStock = redis.call('get', stockKey)

    if not currentStock or tonumber(currentStock) < num then
        return -1 -- 失败：其中一段库存不足，触发“短板效应”
    end
end

-- 3. 扣减阶段：只有当全部通过检查，才真正执行扣减
for i, segId in ipairs(segmentIds) do
    local stockKey = 'stock:seg:' .. segId
    redis.call('decrby', stockKey, num)
end

return 1 -- 成功：多航段原子扣减完成