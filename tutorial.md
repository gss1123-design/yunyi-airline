# 云翼航旅源码学习大纲

## 学习目标与范围

- **项目用途**：Spring Boot 机票预订系统，覆盖航班搜索、预订占座、异步订单创建、支付、超时取消和退款。
- **本次目标**：结合有黑马点评经验的学习者，重点弄清业务模型，以及 Redis Lua 和 RabbitMQ 在真实交易链路中的职责、数据结构、失败分支与一致性边界；不从 Redis 基础命令开始。
- **源码版本**：基于当前工作区，Git 提交 `94c7a04`，分支 `codex/flight-sale-fail-closed`。
- **影响结论的本地改动**：工作区有未提交改动。尤其是 `FlightServiceImpl`、`booking_all_in_one.lua`、`RedisKey` 和新增的 `FlightSaleGuardService` 相关代码，包含“航班 Redis 路由/库存异常时先停售，修复并确认后再恢复”的 fail-closed 路径。以下课程以当前工作区为准。
- **前置基础**：Java/Spring MVC、MyBatis 或 MyBatis-Plus、MySQL 事务、Redis 常用结构与 Lua 基础、RabbitMQ exchange/queue/routing key/ACK 概念。已有黑马点评经验时，可跳过缓存 CRUD、简单缓存穿透等入门复习。
- **覆盖范围**：航班与航段模型、数据生成与索引、搜索、Redis 原子预订、RabbitMQ 异步落库和超时取消、支付/退款状态变更、缓存同步与故障停售。
- **尚未覆盖**：用户注册鉴权细节、支付宝签名配置、WebSocket 消息协议、前端实现、部署脚本和性能压测。本大纲只标出交易学习主线，后续可单独开课。

## 推荐学习顺序

先理解“卖的是什么”以及库存属于哪个对象，再跟一遍搜索和预订。之后学习异步落库与超时回滚，最后看支付、退款和故障恢复。这样读 RabbitMQ 和 Redis 时，能一直围绕订单状态及座位库存的业务结果，而不是孤立背中间件配置。

---

## 第 01 课：航班、航段实例与共享库存

- **读完能回答**：为什么一个航班不直接拥有一份独立库存？多条行程共用航段时，库存和座位该怎样计算？
- **状态**：大纲
- **主链**：B 端 `GET /dev/init` → `DevController.initData` → `BlueprintLoader.loadBlueprints` → `FlightDataService.generateDailyData` → 写入 `flight`、`segment_instance`、`flight_segment`，并预热 Redis 路由与库存。
- **必读路径**：
  1. `src/main/java/com/hakimi/aviation/entity/Flight.java`：定义面向用户出售的行程外壳；输入是蓝图中的航班信息，下一步由关联关系挂接航段，输出是 `flight` 主键。
  2. `src/main/java/com/hakimi/aviation/entity/SegmentInstance.java`：查看真实日期下的航段实例、`availableSeats` 与版本字段；它是实际库存记录。
  3. `src/main/java/com/hakimi/aviation/entity/FlightSegment.java`：查看 `flightId`、`segmentInstanceId`、`segOrder`；它把一条行程按顺序映射到实际航段。
  4. `src/main/java/com/hakimi/aviation/controller/DevController.java` 的 `initData`：确认开发接口怎样加载蓝图并确定日期。
  5. `src/main/java/com/hakimi/aviation/service/flight/FlightDataService.java` 的 `generateDailyData`：看 `todaySegmentCache` 怎样复用同日物理航段，关系记录怎样按顺序写入，以及 `stock:seg:*`、`route:flight:*` 如何预热。
- **选读验证**：`src/main/resources/flight_blueprints.json`；本地数据库里同一个 `segment_instance_id` 是否被不同 `flight_id` 关联。
- **暂缓阅读**：ES 文档组装由第 02 课负责；Redis 座位位图由第 03 课负责；订单字段由第 04 课负责。
- **注意**：仓库有 `component/FlightData/DataInitiator.java`，但当前检索未发现它被 Spring 注册为 Bean；不要先假设启动应用时会自动生成数据。当前可确认的数据生成入口是 `/dev/init`。

## 第 02 课：搜索结果如何拼上实时余票

- **读完能回答**：ES、Redis、MySQL 在搜索时各负责什么？搜索降级后为何可能只展示航班而不能购买？
- **状态**：大纲
- **主链**：`POST /api/v1/pri/flight/search` → `FlightController.searchFlightWithCache` → `FlightServiceImpl.searchFlightWithCache` → ES 查静态航班字段 → Redis 批量读取行程航段和库存 → 组装 `FlightSearchVO`；ES 异常时回退 MySQL，Redis 异常时返回余票未知的静态结果。
- **必读路径**：
  1. `src/main/java/com/hakimi/aviation/controller/FlightController.java` 的 `searchFlightWithCache`：确认请求入口和返回类型。
  2. `src/main/java/com/hakimi/aviation/service/flight/impl/FlightServiceImpl.java` 的 `searchFlightWithCache`、`searchFlightFromES`：输入是城市、日期和排序方式，输出是 ES 命中的静态文档；异常时进入数据库降级。
  3. 同文件 `assembleInventoryFromRedis`：先按航班批量读 `route:flight:*`，再用 `MGET` 获取不重复航段的 `stock:seg:*`，最后把库存装进返回 VO。
  4. `src/main/java/com/hakimi/aviation/es/FlightIndexDoc.java` 与 `src/main/java/com/hakimi/aviation/model/vo/FlightSearchVO.java`：对照 ES 静态字段和面向接口的动态余票字段。
- **选读验证**：`src/main/java/com/hakimi/aviation/service/admin/async/FlightSyncService.java` 的 `syncAllFlights`，观察数据库到 ES、Redis 的构建过程。
- **暂缓阅读**：库存写入与座位位图分配由第 03 课负责；完整数据重建及故障恢复由第 07 课负责。

## 第 03 课：Redis Lua 如何完成一次原子预订

- **读完能回答**：脚本原子地保护了哪些动作？宏观库存和具体座位位图分别解决什么问题？缓存缺失时为什么要快速失败？
- **状态**：大纲
- **主链**：`POST /api/v1/pri/flight/booking` → `FlightController.booking` 从拦截器上下文取登录用户 → `FlightServiceImpl.bookingFlight` 校验请求与用户 → 调用 `booking_all_in_one.lua` → 返回座位偏移或业务错误 → 创建内存订单对象并交给异步服务。
- **必读路径**：
  1. `src/main/java/com/hakimi/aviation/controller/FlightController.java` 的 `booking`：确认用户身份来自服务端请求属性，不依赖请求体中的用户身份。
  2. `src/main/java/com/hakimi/aviation/service/flight/impl/FlightServiceImpl.java` 的 `bookingFlight`：输入为航班、座位偏好和认证用户；看 Lua Key/ARGV 的组装、返回码映射、价格读取和订单对象构建。
  3. `src/main/java/com/hakimi/aviation/common/SeatProbeFactory.java`：看座位偏好怎样转成 Lua 的探测顺序。
  4. `src/main/resources/lua/booking_all_in_one.lua`：按脚本顺序看停售屏障、用户行程防重、路由与库存完整性校验、库存预检、跨航段公共空座探测、扣减库存、设置位图和写入防重记录。
  5. `src/main/java/com/hakimi/aviation/config/RedisKey.java`：把 `order:notFinish:*`、`route:flight:*`、`stock:seg:*`、`seatmap:seg:*` 与脚本中的键对应起来。
- **选读验证**：`src/main/java/com/hakimi/aviation/service/admin/impl/FlightSaleGuardServiceImpl.java` 与 `src/main/java/com/hakimi/aviation/controller/DevController.java` 的停售状态/恢复接口；结合脚本返回码 `-2`、`-5` 理解 fail-closed。
- **暂缓阅读**：RabbitMQ 发布与落库由第 04 课负责；库存回滚由第 05 课负责。

## 第 04 课：RabbitMQ 异步落库与“预订成功”的边界

- **读完能回答**：接口返回预订成功时，订单是否已在 MySQL？topic routing key 如何绑定？消费者如何处理重复消息和落库失败？
- **状态**：大纲
- **主链**：Lua 预订成功 → `FlightServiceImpl.bookingFlight` 组装 `TicketOrder` → `BookingAsyncService.postBookingTasks` 的线程池任务发布订单消息并写订单相关 Redis 状态 → `OrderConsumer.handleOrderCreateMessage` 收到消息后插入 MySQL → 成功 ACK、重复主键 ACK、其他异常 NACK 并重新入队。
- **必读路径**：
  1. `src/main/java/com/hakimi/aviation/service/admin/async/BookingAsyncService.java` 的 `postBookingTasks`：输入是已分配座位的订单；看它怎样先发布订单消息，再写未支付集合、支付快照和超时消息。
  2. `src/main/java/com/hakimi/aviation/message/config/RabbitMQConfig.java` 的订单队列、topic exchange、binding：追踪 routing key `flight.booking.order.{flightId}.{userId}` 怎样匹配 `flight.booking.order.#`。
  3. `src/main/java/com/hakimi/aviation/consumer/OrderConsumer.java` 的 `handleOrderCreateMessage`：看 MySQL 插入、主键冲突幂等和手动 ACK/NACK。
  4. `src/main/java/com/hakimi/aviation/entity/TicketOrder.java` 与 `src/main/java/com/hakimi/aviation/message/order/CancelOrderMessage.java`：确认订单落库消息直接携带 `TicketOrder`，而超时消息另带订单、航班、用户和座位信息。
- **选读验证**：`src/main/java/com/hakimi/aviation/config/ThreadPoolConfig.java` 的 `bookingTaskExecutor`；在开发界面快速预订后观察订单短暂延迟落库的现象。
- **暂缓阅读**：订单支付快照的读取由第 06 课负责；超时队列拓扑由第 05 课负责。
- **讨论边界**：当前预订先修改 Redis，再通过 `@Async` 发布 MQ 消息；Redis、线程池和 RabbitMQ 之间没有同一个事务。学习时要追问进程在 Lua 成功后、消息成功发送前崩溃会留下什么状态，以及怎样用可靠消息/补偿任务改进。

## 第 05 课：TTL、死信路由与未支付库存回滚

- **读完能回答**：RabbitMQ 如何把 15 分钟未支付订单送去取消？取消如何和支付并发竞争？重复消费为什么要幂等？
- **状态**：大纲
- **主链**：预订后发布 `CancelOrderMessage` → 消息进入带 15 分钟 TTL 的死信队列 → TTL 到期后经 dead-letter exchange 转发到取消队列 → `OrderConsumer.handleOrderCancelMessage` 检查未支付记录并条件更新 MySQL 状态 → 执行回滚 Lua 恢复库存/释放座位/清除防重数据 → ACK。
- **必读路径**：
  1. `src/main/java/com/hakimi/aviation/message/config/RabbitMQConfig.java` 的 `orderCancelDeadQueue`、dead-letter exchange、取消队列与 binding：确认 TTL 和死信路由键。
  2. `src/main/java/com/hakimi/aviation/message/order/CancelOrderMessage.java`：确认超时消息带有订单、用户、航班和座位偏移信息。
  3. `src/main/java/com/hakimi/aviation/consumer/OrderConsumer.java` 的 `handleOrderCancelMessage`：看未支付集合删除、条件取消、重投分支及手动 ACK/NACK。
  4. `src/main/resources/lua/rollback_stock.lua`：看库存增加、对应座位位图清零、订单和用户行程集合清理是否在单次脚本中完成。
  5. `src/main/java/com/hakimi/aviation/service/order/impl/OrderServiceImpl.java` 的 `cancelOrder`：对照用户主动取消与 MQ 超时取消共用的业务状态和 Redis 回滚逻辑。
- **选读验证**：`src/main/java/com/hakimi/aviation/config/RedisKey.java` 的未支付、行程、快照 Key；测试界面预订后等待超时前取消，再查看 Redis 和订单状态变化。
- **暂缓阅读**：支付宝支付回调由第 06 课负责；退款消息与退款消费也由第 06 课负责。

## 第 06 课：支付、退款与订单状态机

- **读完能回答**：支付回调如何防重复？为什么订单状态要先做条件更新？已超时却支付成功时怎样兜底？退款为什么另走异步队列？
- **状态**：大纲
- **主链**：支付请求读取 Redis 快照并调用支付宝 → 支付回调校验订单和金额 → 竞争删除 Redis 未支付标记 → MySQL 条件更新为 `PAID` 并扣减航段数据库库存；退款请求条件更新为 `REFUNDING` 并发送 RabbitMQ 消息 → 退款消费者调用网关 → 成功后转 `REFUNDED`、回滚数据库与 Redis 库存并通知用户。
- **必读路径**：
  1. `src/main/java/com/hakimi/aviation/controller/OrderController.java` 的 `payOrder`、`alipayCallBack`、`cancelOrder`、`refundOrder`：确认 HTTP 请求与网关回调入口。
  2. `src/main/java/com/hakimi/aviation/service/order/impl/PayServiceImpl.java` 的 `payOrder`、`confirmOrder`：看支付快照、金额核验、订单状态更新、MySQL 库存兜底和超时后退款分支。
  3. `src/main/java/com/hakimi/aviation/service/order/impl/OrderServiceImpl.java` 的 `refundOrder`、`checkAndParse`、`finishRefund`：看 `PAID → REFUNDING → REFUNDED` 和消息发送失败时恢复状态的处理。
  4. `src/main/java/com/hakimi/aviation/consumer/OrderConsumer.java` 的 `handlerOrderRefundMessage`：看退款锁、数据库状态二次校验、支付宝退款、Redis 回滚、WebSocket 通知和重试分支。
  5. `src/main/resources/mapper/OrderMapper.xml` 与 `src/main/resources/mapper/SegmentInstanceMapper.xml`：核对条件状态更新和真实航段库存 SQL。
- **选读验证**：`src/main/java/com/hakimi/aviation/config/MybatisPlusConfig.java`、`src/main/java/com/hakimi/aviation/mapper/SegmentInstanceMapper.java`。
- **暂缓阅读**：支付宝 SDK 属性和签名配置可在掌握状态机后单独学习；它属于外部网关接入，不是订单主干。

## 第 07 课：缓存同步、故障停售与一致性复盘

- **读完能回答**：数据库同步到 Redis/ES 解决什么问题？缓存不完整时为何停止售票？Redis、MySQL、RabbitMQ 之间有哪些需要补偿的窗口？
- **状态**：大纲
- **主链**：B 端触发同步/恢复 → `FlightSyncService.syncAllFlights` 或停售守卫读取数据库与 Redis → 写入/校验路由、库存、静态索引 → 仅在校验通过后由 B 端解除停售。
- **必读路径**：
  1. `src/main/java/com/hakimi/aviation/service/admin/async/FlightSyncService.java` 的 `syncAllFlights`：看 MySQL 数据怎样批次生成 ES 文档与 Redis 路由/库存。
  2. `src/main/java/com/hakimi/aviation/service/admin/impl/FlightSaleGuardServiceImpl.java`：看停售原因读取和恢复前校验。
  3. `src/main/java/com/hakimi/aviation/controller/DevController.java` 的 `syncFlight`、`getFlightSaleState`、`resumeFlightSale`：确认 B 端运维入口。
  4. 回看 `src/main/resources/lua/booking_all_in_one.lua`：串起缺失数据时设置停售标记和拒绝新预订的处理。
- **选读验证**：`src/main/java/com/hakimi/aviation/service/admin/impl/HandleFlightServiceImpl.java`，看新增航班怎样构造搜索索引和 Redis 缓存。
- **暂缓阅读**：用户认证、部署和完整压测属于独立专题，不影响本课主链。
- **重点复盘**：预订占座立即改变 Redis，但 MySQL 库存主要在支付成功后扣减；而全量同步会按 MySQL 的 `available_seats` 重写 Redis 库存。分析未支付订单恰逢全量同步时会发生什么，并提出防止可售库存被重置的方案。另需留意消费者的 `NACK(requeue=true)` 当前没有看到有限重试/退避策略，故障时要评估消息快速重投风险。

## 大纲完成后的学习方法

每课都用同一张纸记录四件事：业务对象和状态、Redis Key 或 MQ 路由、成功后的数据写入、失败后的补偿或快速失败。读完一课后先自己口述主链，再用本地测试前端触发请求，最后对照数据库、Redis 和 RabbitMQ 队列确认状态变化。对支付沙箱等外部配置不通的路径，先读状态和调用代码，不把配置失败误当成业务链路验证通过。

当前仓库已有前端测试页：`frontend/index.html` 与 `frontend/app.js`。后端接口字段和登录方式以 `APIs.md` 为准。
