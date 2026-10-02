# 云翼航旅 (Yunyi Air) — 项目目录结构

> 基于 Spring Boot 3.5 + JDK 17 的高并发机票预订系统，核心交易链路已打通。

```
aviation-system/
│
├── .gitattributes                     # Git 属性配置（换行符策略等）
├── .gitignore                         # Git 忽略规则
├── HELP.md                            # Spring Boot 帮助文档（脚手架自带）
├── APIs.md                            # API 接口文档（RESTful 规范、鉴权说明）
├── README.md                          # 项目 README — 核心特性、技术栈、快速启动
├── pom.xml                            # Maven 项目配置（依赖、插件、Spring Boot 3.5.13）
├── mvnw                               # Maven Wrapper（Linux/Mac 可执行脚本）
├── mvnw.cmd                           # Maven Wrapper（Windows 批处理脚本）
│
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties   # Maven Wrapper 版本配置
│
├── .idea/                             # IntelliJ IDEA 项目配置（编译器、编码、检查项、VCS等）
│   ├── .gitignore
│   ├── compiler.xml
│   ├── encodings.xml
│   ├── misc.xml
│   ├── vcs.xml
│   ├── workspace.xml
│   ├── jarRepositories.xml
│   ├── dictionaries/
│   │   └── project.xml
│   └── inspectionProfiles/
│       └── Project_Default.xml
│
└── src/
    ├── main/
    │   ├── java/com/hakimi/aviation/
    │   │   │
    │   │   ├── AviationSystemApplication.java  # 🚀 Spring Boot 应用主入口
    │   │   │
    │   │   ├── alipay/                          # 💰 支付宝支付模块
    │   │   │   ├── AlipayConfig.java            #   支付宝 SDK Client 配置（读取 AlipayConfigProperties）
    │   │   │   ├── AlipayConfigProperties.java  #   支付宝参数配置类（appId、私钥、公钥等）
    │   │   │   ├── AlipayProcess.java           #   支付宝业务处理（支付、退款、查询、回调验签）
    │   │   │   └── AlipayCallbackUtil.java      #   支付宝异步回调校验与解析工具
    │   │   │
    │   │   ├── annotations/                     # 🏷️ 自定义注解
    │   │   │   └── LoginOptional.java           #   @LoginOptional — 标注接口可选登录（游客/用户均可访问）
    │   │   │
    │   │   ├── aspect/                          # （预留：AOP 切面模块）
    │   │   │
    │   │   ├── common/                          # 📦 通用类
    │   │   │   ├── JsonData.java                #   统一响应体封装（code、msg、data + 静态工厂方法）
    │   │   │   └── SeatProbeFactory.java        #   座位探测工厂 — 加载座位探测策略
    │   │   │
    │   │   ├── component/
    │   │   │   ├── FlightData/                  # ✈️ 航班数据初始化组件
    │   │   │   │   ├── BlueprintLoader.java     #   航班蓝图加载器（从 JSON 读取 flight_blueprints.json）
    │   │   │   │   └── DataInitiator.java       #   数据初始化器 — 启动时将蓝图实例化为指定日期的航班/航段数据
    │   │   │   └── cache/                       # （预留：自定义缓存组件）
    │   │   │
    │   │   ├── config/                          # ⚙️ Spring 配置类
    │   │   │   ├── InterceptorConfig.java       #   拦截器注册配置（CORS、登录拦截器 + 路径白名单）
    │   │   │   ├── MybatisPlusConfig.java       #   MyBatis-Plus 分页插件配置
    │   │   │   ├── RedisKey.java                #   Redis Key 常量定义（航班缓存、库存、订单锁等前缀）
    │   │   │   ├── RedisTemplateConfig.java     #   RedisTemplate 序列化配置
    │   │   │   └── ThreadPoolConfig.java        #   线程池配置（用于异步任务）
    │   │   │
    │   │   ├── consumer/                        # 📩 消息消费者
    │   │   │   └── OrderConsumer.java           #   RabbitMQ 订单消息消费者（取消订单等异步处理）
    │   │   │
    │   │   ├── controller/                      # 🌐 REST 控制器
    │   │   │   ├── DevController.java           #   开发者接口（数据初始化、测试端点）
    │   │   │   ├── FlightController.java        #   航班接口（搜索、创建航班）
    │   │   │   ├── OrderController.java         #   订单接口（预订、支付、取消、查询）
    │   │   │   └── UserController.java          #   用户接口（注册、登录、验证码）
    │   │   │
    │   │   ├── dto/                             # 📋 数据传输对象
    │   │   │   └── FlightBlueprintDTO.java      #   航班蓝图 DTO（日期、航线、价格、座位配置等）
    │   │   │
    │   │   ├── entity/                          # 🗃️ 数据库实体
    │   │   │   ├── Flight.java                  #   航班实体（航班号、日期、起降信息、价格、库存）
    │   │   │   ├── FlightSegment.java           #   航段实体（航班内的一段：起降城市、机场、时间）
    │   │   │   ├── SegmentInstance.java         #   航段实例（具体日期的航段 + 座位库存位图）
    │   │   │   ├── TicketOrder.java             #   订单实体（订单号、状态、金额、座位、乘客信息）
    │   │   │   └── User.java                    #   用户实体（邮箱、密码、昵称）
    │   │   │
    │   │   ├── enums/                           # 📎 枚举类
    │   │   │   └── BizCodeEnum.java             #   业务状态码枚举（成功、各类异常码映射）
    │   │   │
    │   │   ├── es/                              # 🔍 Elasticsearch 索引
    │   │   │   └── FlightIndexDoc.java          #   航班 ES 索引文档（供搜索用）
    │   │   │
    │   │   ├── exception/                       # ❗ 异常处理
    │   │   │   ├── BizException.java            #   自定义业务异常（携带 BizCodeEnum）
    │   │   │   └── CustomExceptionHandler.java  #   全局异常处理器（@ControllerAdvice，统一返回 JsonData）
    │   │   │
    │   │   ├── interceptor/                     # 🛡️ 拦截器
    │   │   │   ├── CorsInterceptor.java         #   CORS 跨域拦截器
    │   │   │   └── LoginInterceptor.java        #   登录鉴权拦截器（校验 JWT Token / @LoginOptional 跳过）
    │   │   │
    │   │   ├── mapper/                          # 🗺️ MyBatis-Plus Mapper 接口
    │   │   │   ├── FlightMapper.java            #   航班 Mapper
    │   │   │   ├── FlightSegmentMapper.java     #   航段 Mapper
    │   │   │   ├── OrderMapper.java             #   订单 Mapper
    │   │   │   ├── SegmentInstanceMapper.java   #   航段实例 Mapper
    │   │   │   └── UserMapper.java              #   用户 Mapper
    │   │   │
    │   │   ├── message/                         # 📨 RabbitMQ 消息模块
    │   │   │   ├── config/
    │   │   │   │   └── RabbitMQConfig.java      #   RabbitMQ 交换机/队列/绑定声明与配置
    │   │   │   ├── flight/                      #   （预留：航班相关消息）
    │   │   │   └── order/
    │   │   │       ├── OrderMessage.java        #   订单消息体（创建/支付）
    │   │   │       └── CancelOrderMessage.java  #   取消订单消息体
    │   │   │
    │   │   ├── model/                           # 📥📤 请求 / 响应模型
    │   │   │   ├── request/
    │   │   │   │   ├── flight/
    │   │   │   │   │   ├── BookingRequest.java      #   预订下单请求
    │   │   │   │   │   ├── CreateFlightRequest.java #   管理端创建航班请求
    │   │   │   │   │   ├── FlightSearchRequest.java #   航班搜索请求（起止城市、日期）
    │   │   │   │   │   └── PayOrderRequest.java     #   支付订单请求
    │   │   │   │   ├── order/
    │   │   │   │   │   └── CancelOrderRequest.java  #   取消订单请求
    │   │   │   │   └── user/
    │   │   │   │       ├── LoginRequest.java        #   登录请求
    │   │   │   │       ├── RegisterRequest.java     #   注册请求
    │   │   │   │       └── SendCodeRequest.java     #   发送验证码请求
    │   │   │   └── vo/                              #   ViewObject 响应视图
    │   │   │       ├── CancelOrderVO.java           #   取消订单结果 VO
    │   │   │       ├── FlightSearchVO.java          #   航班搜索结果 VO
    │   │   │       ├── FlightSaleStateVO.java       #   B端航班故障停售状态 VO
    │   │   │       └── TicketOrderVO.java           #   订单详情 VO
    │   │   │
    │   │   ├── repository/                      # 📚 数据仓库层（非 MyBatis 的数据访问）
    │   │   │   └── FlightIndexRepository.java   #   ES 航班索引仓库（搜索查询封装）
    │   │   │
    │   │   ├── script/                          # 📜 Redis Lua 脚本加载器
    │   │   │   └── LuaScript.java               #   加载并缓存 Lua 脚本到 Redis（booking/deduct/rollback）
    │   │   │
    │   │   ├── service/                         # 🧠 业务服务层
    │   │   │   ├── admin/
    │   │   │   │   ├── HandleFlightService.java         #   管理端航班管理服务接口
    │   │   │   │   ├── FlightSaleGuardService.java      #   航班故障停售查询与恢复接口
    │   │   │   │   ├── impl/
    │   │   │   │   │   ├── HandleFlightServiceImpl.java #   航班管理实现（创建航班、数据同步）
    │   │   │   │   │   └── FlightSaleGuardServiceImpl.java # 航班停售校验与恢复实现
    │   │   │   │   └── async/
    │   │   │   │       ├── BookingAsyncService.java     #   预订异步服务（下单后异步落库/同步 ES）
    │   │   │   │       ├── FlightSyncService.java       #   航班数据同步服务（缓存与 DB 同步）
    │   │   │   │       └── UserDataAsyncService.java    #   用户数据异步服务
    │   │   │   ├── flight/
    │   │   │   │   ├── FlightDataService.java           #   航班数据服务接口
    │   │   │   │   ├── FlightService.java               #   航班查询服务接口
    │   │   │   │   └── impl/
    │   │   │   │       └── FlightServiceImpl.java       #   航班查询实现（ES+Redis 二级缓存 + DB 降级）
    │   │   │   ├── order/
    │   │   │   │   ├── OrderService.java                #   订单服务接口
    │   │   │   │   ├── PayService.java                  #   支付服务接口
    │   │   │   │   └── impl/
    │   │   │   │       ├── OrderServiceImpl.java        #   订单实现（预订下单、取消、Lua 原子扣库存）
    │   │   │   │       └── PayServiceImpl.java          #   支付实现（支付宝对接、回调处理）
    │   │   │   └── user/
    │   │   │       ├── UserService.java                 #   用户服务接口
    │   │   │       └── UserServiceImpl.java             #   用户实现（注册、登录、JWT 签发、邮箱验证码）
    │   │   │
    │   │   └── util/                            # 🔧 工具类
    │   │       ├── AirportCityUtil.java         #   机场<->城市映射工具
    │   │       ├── EmailUtil.java               #   邮件发送工具（验证码邮件）
    │   │       ├── JWTUtils.java                #   JWT 令牌生成与校验工具
    │   │       ├── MD5Util.java                 #   MD5 加密工具（密码加盐哈希）
    │   │       └── ValidateRequest.java         #   请求参数校验工具（空值、格式检查）
    │   │
    │   └── resources/
    │       ├── GEMINI.md                        #   AI 辅助开发说明文档
    │       ├── application.yaml                 #   Spring Boot 主配置文件（DB、Redis、ES、MQ、支付宝等）
    │       ├── logs.txt                         #   日志文件
    │       ├── flight_blueprints.json           #   航班蓝图数据（航线模板 JSON）
    │       ├── lua/
    │       │   ├── booking_all_in_one.lua       #   Redis Lua: 预订原子操作（查库存→占座位→扣库存）
    │       │   ├── deduct_stock.lua             #   Redis Lua: 库存扣减脚本
    │       │   └── rollback_stock.lua           #   Redis Lua: 库存回滚脚本（取消/超时）
    │       ├── mapper/
    │       │   ├── FlightMapper.xml             #   航班 SQL XML 映射
    │       │   ├── FlightSegmentMapper.xml      #   航段 SQL XML 映射
    │       │   ├── OrderMapper.xml              #   订单 SQL XML 映射
    │       │   ├── SegmentInstanceMapper.xml    #   航段实例 SQL XML 映射
    │       │   └── UserMapper.xml               #   用户 SQL XML 映射
    │       ├── static/                          #   （预留：静态资源目录）
    │       └── templates/
    │           └── email-code.html              #   邮箱验证码 HTML 模板
    │
    └── test/
        ├── java/com/hakimi/aviation/
        │   ├── AviationSystemApplicationTests.java  # 🧪 Spring Boot 基础集成测试
        │   └── JMeterDataGenerator.java             #   JMeter 压测数据生成器（批量生成用户+Token CSV）
        └── resources/
            └── jmeter_users_data.csv                #   生成的 JMeter 测试用户数据示例
```

## 架构分层速览

| 层 | 目录 | 职责 |
|----|------|------|
| **Controller** | `controller/` | 接收 HTTP 请求，参数校验，调用 Service |
| **Service** | `service/` | 核心业务逻辑编排，事务管理 |
| **Mapper/Repository** | `mapper/` + `repository/` | 数据访问（MyBatis-Plus + ES） |
| **Entity/Model** | `entity/` + `model/` | 数据库映射实体 + 请求/响应 DTO/VO |
| **Config** | `config/` | Spring Bean 配置、拦截器注册 |
| **Interceptor** | `interceptor/` | 请求拦截（鉴权、跨域） |
| **Exception** | `exception/` | 统一异常处理与业务异常定义 |
| **Util** | `util/` | 通用工具函数 |
| **Consumer** | `consumer/` | RabbitMQ 消息监听与异步处理 |
| **Script** | `script/` + `lua/` | Redis Lua 原子脚本 |

## 核心技术栈

- **框架**: Spring Boot 3.5.13 + MyBatis-Plus
- **数据库**: MySQL（业务数据）+ Redis（缓存/库存/Lua）+ Elasticsearch（搜索）
- **消息队列**: RabbitMQ（订单异步处理）
- **支付**: 支付宝 SDK（支付/退款/回调）
- **鉴权**: JWT（无状态身份认证）
- **其他**: JavaMail（邮件验证码）、Jackson（JSON）

---

*最后更新时间: 2026-06-05*
