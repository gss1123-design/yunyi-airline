# Hakimi Airline JMeter 压测与购票链路验证

这组计划覆盖航班查询、Redis Lua 并发占座、异步订单落库和订单取消。JMeter 5.6.3 与 Java 21 在本机运行；应用和 MySQL、Redis、RabbitMQ、Elasticsearch 运行在同一台机器的 Docker 中。

## 文件

| 文件 | 用途 |
| --- | --- |
| `flight-search.jmx` | 查询压测，可切换 ES + Redis 查询和 MySQL 查询 |
| `flight-booking.jmx` | 一用户一请求，支持同步定时器制造并发抢票；预期售罄和技术错误分开统计 |
| `flight-cancel.jmx` | 取消 CSV 中列出的测试订单 |
| `GenerateJMeterUsers.ps1` | 使用正在运行的应用签名密钥生成临时测试 JWT 和购票 CSV |
| `GenerateJMeterCancelUsers.ps1` | 从 `ticket_order` 读取测试用户的未支付订单，并配对 JWT 生成取消 CSV |

JMeter 和运行结果在被 Git 忽略的 `target/` 下，不会把本机生成的 JWT 或 JTL 数据提交到仓库。

## 本次测试口径

- 查询日期：`2026-10-10`；本地数据集包含 62 个航班。
- ES + Redis 接口：`POST /api/v1/pri/flight/search`。
- MySQL 直查接口：`POST /api/v1/pri/flight/search_flight`。
- 查询测试每档 5,000 次请求，ramp-up 5 秒；JTL 的 P95/P99 使用样本 `elapsed` 计算。
- 购票使用航班 25 和 26。它们共享航段实例 25，该航段初始 Redis 库存为 180；200 个唯一用户由同步定时器同时放行。
- 取消压测针对 180 笔实际创建成功的订单，180 个请求在 5 秒内启动。

| 场景 | 并发 / 请求数 | 吞吐 | 平均 | P95 | P99 | 最大 | HTTP/技术错误 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| ES + Redis 查询 | 50 / 5,000 | 944.8 req/s | 6.10 ms | 12 ms | 17 ms | 51 ms | 0 |
| ES + Redis 查询 | 100 / 5,000 | 965.8 req/s | 6.44 ms | 12 ms | 21 ms | 50 ms | 0 |
| ES + Redis 查询 | 200 / 5,000 | 981.7 req/s | 9.35 ms | 21 ms | 29 ms | 59 ms | 0 |
| MySQL 直查 | 50 / 5,000 | 924.2 req/s | 5.88 ms | 11 ms | 15 ms | 58 ms | 0 |
| 并发购票 | 200 / 200 | 不作为吞吐指标 | 335.82 ms | 460 ms | 466 ms | 467 ms | 0 |
| 并发取消 | 180 / 180 | 35.7 req/s | 19.21 ms | 86 ms | 90 ms | 94 ms | 0 |

购票的 200 个结果中，180 笔为 `Booking-SUCCESS`，20 笔为预期的 `Booking-SOLD_OUT_EXPECTED`，没有技术失败。落库核对得到 180 个不同的座位偏移量：航班 25 占 88 张、航班 26 占 92 张；共享航段库存没有超过 180。180 笔取消全部成功，随后 Redis 库存恢复为 180、座位位图归零。测试订单行和生成用户的 Redis 行程键已经清理。

原始 JTL 位于 `target/performance-results/`：`search-es-redis-{50,100,200}-steady.jtl`、`search-db-50-steady.jtl`、`booking-shared-segment-200.jtl`、`booking-cancel-180.jtl`。购票和取消的 HTML 汇总分别在 `booking-shared-segment-200-report/`、`booking-cancel-180-report/`。

购票 JMeter 运行时包含 5 秒 ramp-up 和同步放行等待，因此没有把它的整轮平均速率包装成接口 QPS。订单超时消息会在 RabbitMQ TTL 到期后检查未支付集合；已取消订单不再属于该集合，消费者会将对应的延迟消息确认并丢弃。

## 50,000 次查询扩测

随后将查询扩到每档 50,000 次，仍使用 5 秒 ramp-up。这里的请求总量是 50,000，虚拟用户并发分别为 50、100、200，并非 50,000 个用户同时请求。ES + Redis 跑 50、100、200 并发；MySQL 直查跑 50 并发作同请求量对照。

| 场景 / 并发 / 请求数 | 吞吐 | 平均 | P95 | P99 | 最大 | 超过 1 秒 | 错误 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| ES + Redis / 50 / 50,000（首轮） | 970 req/s | 48.71 ms | 123 ms | 247 ms | 5,397 ms | 46 | 0 |
| ES + Redis / 50 / 50,000（复跑） | 2,468 req/s | 16.07 ms | 28 ms | 37 ms | 81 ms | 0 | 0 |
| ES + Redis / 100 / 50,000 | 2,194 req/s | 38.34 ms | 71 ms | 99 ms | 290 ms | 0 | 0 |
| ES + Redis / 200 / 50,000 | 2,461 req/s | 65.37 ms | 115 ms | 141 ms | 218 ms | 0 | 0 |
| MySQL 直查 / 50 / 50,000 | 1,911 req/s | 22.81 ms | 53 ms | 80 ms | 516 ms | 0 | 0 |

首轮 50 并发的 46 个 1 秒以上请求全部落在 ramp-up 的前 5 秒；复跑时没有超过 1 秒的请求。JMeter 四轮扩测均未记录 HTTP 或断言错误。当前未采集应用 JVM、Docker CPU、GC 和线程池指标，不能确认首轮长尾来源。扩测的吞吐比 5,000 次短测更能反映持续发送时的速率；短测的时长接近 ramp-up 时间，不能直接和长测吞吐横向比较。

在本次同机、小数据集的对照中，ES + Redis 50 并发复跑吞吐约 2,468 req/s，MySQL 直查约 1,911 req/s；这只描述本地测得的差异，不能外推到生产环境。扩测结果和 HTML 报告在 `target/performance-results/`：`search-es-redis-{50,100,200}-50k.jtl`、对应的 `*-50k-report/`、50 并发复跑的 `search-es-redis-50-50k-repeat.jtl` 和 `search-es-redis-50-50k-repeat-report/`，以及 MySQL 直查的 `search-db-50-50k.jtl` 和 `search-db-50-50k-report/`。

## 复跑

在仓库根目录用 PowerShell 执行。以下默认后端地址是 `127.0.0.1:18080`，需要运行中的本机 Docker 服务。

### 查询：50 并发，5,000 次

```powershell
$jmeter = (Resolve-Path '.\target\jmeter\apache-jmeter-5.6.3\bin\jmeter.bat').Path
New-Item -ItemType Directory -Force target/performance-results | Out-Null
& $jmeter -n -t performance/jmeter/flight-search.jmx `
  '-Jusers=50' '-Jloops=100' '-Jramp=5' `
  '-JflightDate=2026-10-10' '-Jpath=/api/v1/pri/flight/search' `
  -l target/performance-results/search.jtl
```

将 `users` 改为 100 或 200，并保持 `loops=100`，即可分别发送 10,000 或 20,000 次请求；要复现本次每档 5,000 次结果，应分别设置 `loops=100`、`50`、`25`。直查接口把 `path` 改为 `/api/v1/pri/flight/search_flight`。

复现 50,000 次扩测时，分别使用 `(users, loops)=(50,1000)、(100,500)、(200,250)`，并为每次运行设置不同的 `-l` 输出文件。若需比较稳态，可以先用较短的预热请求跑通缓存，再单独记录正式轮次，保留 ramp-up 阶段的长尾数据。

### 并发购票和取消

购票测试会创建数据库订单并扣 Redis 库存，只应在本地隔离环境执行。使用前确认测试用户 ID `900000–900199` 没有现存订单；航班 25、26 的 Redis 路由和库存必须与数据库一致。本次验证中两班共享航段 25，初始库存 180。若需用 `/dev/flight/sync` 重建 Redis 航班数据，先确认没有其他未支付订单，因为全量同步会按数据库库存重写 Redis 库存。

```powershell
$jmeter = (Resolve-Path '.\target\jmeter\apache-jmeter-5.6.3\bin\jmeter.bat').Path
.\performance\jmeter\GenerateJMeterUsers.ps1 -FirstUserId 900000 -Count 200 -FlightIdA 25 -FlightIdB 26

# 为新测试用户预置空行程集合哨兵；认证信息在 Redis 容器内部读取，不要在命令行中填写密码。
docker exec hakimi-airline-local-redis-1 sh -lc 'export REDISCLI_AUTH="$REDIS_PASSWORD"; for i in $(seq 900000 900199); do redis-cli --no-auth-warning -n 1 SADD "order:notFinish:$i" -1 >/dev/null; redis-cli --no-auth-warning -n 1 EXPIRE "order:notFinish:$i" 604800 >/dev/null; done'

& $jmeter -n -t performance/jmeter/flight-booking.jmx `
  '-Jusers=200' '-Jloops=1' '-Jramp=5' '-JsyncUsers=200' `
  '-JusersCsv=target/performance-results/booking-users.csv' `
  -l target/performance-results/booking.jtl

# 购票接口异步创建订单；先等 RabbitMQ 消费完成，并确认 ticket_order 中已有所有成功订单。
.\performance\jmeter\GenerateJMeterCancelUsers.ps1 -FirstUserId 900000 -Count 200
& $jmeter -n -t performance/jmeter/flight-cancel.jmx `
  '-Jusers=180' '-Jramp=5' `
  '-JcancelCsv=target/performance-results/cancel-users.csv' `
  -l target/performance-results/cancel.jtl
```

如果成功订单数不是 180，取消 JMeter 的 `users` 应设为取消 CSV 行数。取消成功后，只清理确认已经 `CANCELLED` 的测试订单行，并删除 ID 区间内的 `order:notFinish:*`、`order:unpaid:*` 测试键；不要清空共享队列或全局 Redis 数据。`GenerateJMeterUsers.ps1` 会从运行中的 app 容器读取 JWT 签名密钥，只在进程环境中临时使用，并恢复原环境变量。

## 结果限制

这是同一台本机上的 JMeter 与 Docker 应用压测，数据规模为 62 个航班，不能代表生产环境容量或 SLA。查询延迟很低且 ES + Redis 与 MySQL 直查接近，主要反映当前小数据集和本机网络条件。真实容量评估应将 JMeter 放到独立压测机，扩大航班数据量，并分离数据库、Redis、MQ 和应用节点，同时记录 CPU、内存、连接池、GC、队列积压和数据库慢查询。
