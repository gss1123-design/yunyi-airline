# 云翼航旅 API 文档（前端交付版）

> 本文件是**后端对外接口的唯一权威契约**。README.md 只讲架构与原理，接口一律以本文件为准。
> 新增/变更接口时，**只改这里**。
>
> 最后更新：2026-09-27

---

## 目录

- [一、基础约定](#一基础约定)
  - [1.1 通用响应体](#11-通用响应体)
  - [1.2 鉴权](#12-鉴权)
  - [1.3 日期时间格式](#13-日期时间格式)
  - [1.4 全局错误码表](#14-全局错误码表)
- [二、接口总览](#二接口总览)
- [三、用户模块](#三用户模块)
- [四、航班搜索模块](#四航班搜索模块)
- [五、预订模块](#五预订模块)
- [六、订单与支付模块](#六订单与支付模块)
- [七、实时通知（WebSocket）](#七实时通知websocket)
- [八、订单状态机](#八订单状态机)
- [九、B 端故障停售接口](#九b-端故障停售接口)

---

## 一、基础约定

> ### 🔧 BaseURL 约定（联调阶段，务必先读）
>
> 开发联调阶段，**前端与后端运行在同一台设备**（本机），后端服务地址固定为：
>
> ## `http://127.0.0.1:8080/api/v1`
>
> 前端 HTTP 客户端（axios / fetch）的 `baseURL` 直接设为上面这一串。
>
> ⚠️ **防止重复前缀（重要）**：本文档每个接口标注的都是**完整绝对路径**，本身已带 `/api/v1`（例如 `/api/v1/pri/user/login`）。而 `baseURL` 里**也已经包含了 `/api/v1`**，所以在代码里发请求时**只写 `/api/v1` 之后的部分**（即以 `/pri/...` 开头），否则会拼成 `http://127.0.0.1:8080/api/v1/api/v1/...` 这样的错误地址。
>
> | 项 | 值（以「用户登录」为例） |
> |---|---|
> | `baseURL` | `http://127.0.0.1:8080/api/v1` |
> | ✅ 代码里写的请求路径 | `/pri/user/login` |
> | ❌ 错误写法 | `/api/v1/pri/user/login`（会变成 `.../api/v1/api/v1/pri/user/login`） |
> | 最终实际请求的完整 URL | `http://127.0.0.1:8080/api/v1/pri/user/login` |
>
> 📌 **规律**：所有前端接口都在 `/api/v1/pri(或pub)/...` 下；去掉 `baseURL` 已含的 `/api/v1`，代码里一律以 **`/pri(或pub)/...`** 开头调用。
> 📌 **例外**：WebSocket 不走此 baseURL，直接连 `ws://127.0.0.1:8080/ws/notifications/{userId}`（见 [第七章](#七实时通知websocket)）。

- **协议**：HTTP（本机联调；生产环境走 HTTPS）
- **数据格式**：`application/json`（除支付接口返回 HTML，详见 [6.1](#61-发起支付-核心必读)）

### 1.1 通用响应体

所有 JSON 接口都返回统一结构 `JsonData`：

```json
{
  "code": 200,
  "data": {},
  "msg": "操作成功",
  "timestamp": 1754912345678
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | **成功恒为 `200`**；业务失败为对应错误码；未知/系统异常为 `-1` |
| `data` | any | 业务数据，可能是对象、数组、字符串或 `null` |
| `msg` | string | 提示文案，可直接展示给用户 |
| `timestamp` | long | 服务器毫秒时间戳 |

> ⚠️ **判断成功的唯一标准是 `code === 200`**。注意 code 不是 0（枚举里的 `SUCCESS(0)` 未用于响应）。

### 1.2 鉴权

- 登录成功后，`data` 即为**完整 token 字符串**（已含 `hajimi` 前缀，例：`hajimieyJhbGci...`）。
- 需要登录的接口，在 **请求头** 携带：`token: hajimi....`（把登录拿到的字符串**原样**放进去，不要再加前缀）。
  - 也支持用 Query 传：`?token=hajimi....`
- token 有效期 **7 天**，过期或伪造返回 `{"code":-1,"msg":"登录过期，请重新登录"}`。

**关于 `userId`**：JWT 载荷里带有 `id`（用户ID）、`name`、`head_img`。个别接口（如预订）请求体需要 `userId`，前端可对 token 解码获取：去掉 `hajimi` 前缀后，取中间的 payload 段做 Base64Url 解码，得到 `{"id":..., "name":"...", ...}`。

**鉴权分级**：

| 级别 | 含义 | 涉及接口 |
|---|---|---|
| 🟢 免登录 | 不需要 token | 图形验证码、发送邮箱验证码、注册、登录、支付回调 |
| 🟡 可选登录 | 带 token 会校验，不带也放行 | 航班搜索、降级搜索 |
| 🔴 必须登录 | 不带/无效 token 直接拦截 | 预订、支付、取消、退款、订单列表、订单详情 |

### 1.3 日期时间格式

| 类型 | 格式 | 示例 |
|---|---|---|
| 日期 `LocalDate` | `yyyy-MM-dd` | `2026-08-20` |
| 时间 `LocalDateTime`（默认） | ISO-8601 `yyyy-MM-dd'T'HH:mm:ss` | `2026-08-20T08:00:00` |
| ⚠️ 例外：`CancelOrderVO.cancelTime` | `yyyy-MM-dd HH:mm:ss`（GMT+8） | `2026-08-20 08:00:00` |

### 1.4 全局错误码表

前端据此做错误提示（`code !== 200` 时读 `msg`，或按码定制交互）。

| code | 含义 | 典型场景 |
|---|---|---|
| `200` | 成功 | 所有成功响应 |
| `-1` | 通用失败 / 未知异常 | 登录态失效、系统异常、`买/登录`兜底失败 |
| `1` | 服务繁忙，请稍后再试 | Redis/Lua 繁忙、抢票并发兜底 |
| `2` | 验证码发送过于频繁 | 60s 冷却期内重复发码 |
| `3` | 操作频繁，请稍后重试 | 同邮箱并发注册 |
| `4` | 非法请求，缺少必需属性 | DTO 校验失败、`userId` 与 token 不一致 |
| `201` | 图形验证码不能为空 | send_code 未传 picCode |
| `202` | 图形验证码错误或已过期 | 图形码错/超 120s |
| `203` | 邮箱验证码不能为空 | — |
| `204` | 邮箱验证码错误或已过期 | 邮箱码错/超 300s |
| `205` | 缺少必要的注册信息 | 注册字段不全 |
| `206` | 验证会话已过期，请重新获取验证码 | sessionToken 失效（超 300s） |
| `207` | 验证码邮箱与注册邮箱不一致 | 换邮箱注册 |
| `208` | 邮箱或密码错误 | 登录失败 |
| `301` | 已购买过此航班 | 重复购票 |
| `302` | 机票已售罄 | 无库存/无公共空座 |
| `303` | 航班已下架或数据未就绪 | 航段数据缺失，需 B 端处理 |
| `304` | 订单不存在或已超时 | 订单查无/超时/非本人 |
| `305` | 退款失败（重复请求或无效退款） | 退款状态机拦截 |
| `306` | 当前订单不允许退款：状态异常 | 非 PAID 订单申请退款 |
| `307` | 航班信息异常，退款失败 | 退款时航班信息缺失 |
| `308` | 第三方网关退款失败，请联系客服 | 支付宝退款接口报错 |
| `309` | 订单状态更新失败 | 退款收尾更新失败 |
| `401` | 航段配置非法 | B 端建航班 |

---

## 二、接口总览

> 下表「路径」为**完整绝对路径**。联调时请按 [§1 BaseURL 约定](#一基础约定) 去掉 `baseURL` 已含的 `/api/v1`，代码里以 `/pri/...` 开头调用，切勿重复拼接 `/api/v1`。

| # | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|
| 3.1 | GET | `/api/v1/pri/user/captcha` | 🟢 | 获取图形验证码 |
| 3.2 | POST | `/api/v1/pri/user/send_code` | 🟢 | 发送邮箱验证码（返回 sessionToken） |
| 3.3 | POST | `/api/v1/pri/user/register` | 🟢 | 注册 |
| 3.4 | POST | `/api/v1/pri/user/login` | 🟢 | 登录（返回 token） |
| 4.1 | POST | `/api/v1/pri/flight/search` | 🟡 | 高性能搜索（含实时余票） |
| 4.2 | POST | `/api/v1/pri/flight/search_flight` | 🟡 | 数据库降级搜索 |
| 5.1 | POST | `/api/v1/pri/flight/booking` | 🔴 | 抢票预订 |
| 6.1 | POST | `/api/v1/pri/order/pay` | 🔴 | 发起支付（返回支付宝 HTML） |
| 6.2 | POST | `/api/v1/pri/order/pay/callback` | 🟢 | 支付宝异步回调（**前端不调用**） |
| 6.3 | POST | `/api/v1/pri/order/cancel` | 🔴 | 取消未支付订单 |
| 6.4 | POST | `/api/v1/pri/order/refund` | 🔴 | 已支付订单退款 |
| 6.5 | GET | `/api/v1/pri/order/list` | 🔴 | 我的订单列表 |
| 6.6 | GET | `/api/v1/pri/order/detail` | 🔴 | 订单详情 |
| 7 | WS | `/ws/notifications/{userId}` | — | 实时通知推送 |
| 9.1 | GET | `/dev/flight/{flightId}/sale-state` | 内部运维 | 查询航班故障停售状态 |
| 9.2 | POST | `/dev/flight/{flightId}/sale-resume` | 内部运维 | 校验 Redis 数据后恢复销售 |

> B 端 / 运维接口（`/dev/*`）为内部使用，不在前端交付范围。

---

## 三、用户模块

### 注册流程（三步，务必按序）

```
① GET  captcha        → 拿到 { uuid, imgBase64 }，页面展示图片
② POST send_code      → 传 { email, picCode, captchaKey=uuid } → 拿到 { sessionToken }
③ POST register       → 传 { email, userName, password, verifyCode, sessionToken }
```

> `sessionToken` 用于绑定「收到邮箱验证码的邮箱」与「注册邮箱」，两者必须一致，防止盗用他人验证码。

> 🔐 **密码传输**：前端**发送明文密码**（依赖 HTTPS 保护），后端负责 MD5 存储。请勿在前端 MD5。（`RegisterRequest` 源码注释「密码已经过 MD5」为历史误注，以此文档为准。）

---

### 3.1 获取图形验证码

- **`GET /api/v1/pri/user/captcha`**
- 鉴权：🟢 免登录
- 请求参数：无
- **响应 `data`**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `uuid` | string | 验证码会话标识，下一步作为 `captchaKey` 回传 |
| `imgBase64` | string | 图片 Base64（`data:image/png;base64,...`），直接塞 `<img src>` |

```json
{ "code": 200, "msg": "图形验证码信息已发送至前端",
  "data": { "uuid": "3f2a...e91", "imgBase64": "data:image/png;base64,iVBORw0K..." } }
```

> 图形验证码有效期 **120 秒**，大小写不敏感。

### 3.2 发送邮箱验证码

- **`POST /api/v1/pri/user/send_code`**
- 鉴权：🟢 免登录
- **请求体**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `email` | string | 是 | 注册邮箱 |
| `picCode` | string | 是 | 用户输入的图形验证码文本 |
| `captchaKey` | string | 是 | 上一步返回的 `uuid` |

- **响应 `data`**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionToken` | string | 会话令牌，注册时必须原样回传 |

```json
{ "code": 200, "msg": "验证码已发送至您的邮箱", "data": { "sessionToken": "a1b2c3...ff" } }
```

> 邮箱验证码有效期 **300 秒**；同邮箱 **60 秒** 冷却（期内重发返回 `code 2`）。图形码错误返回 `code 202`。

### 3.3 用户注册

- **`POST /api/v1/pri/user/register`**
- 鉴权：🟢 免登录
- **请求体**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `email` | string | 是 | 与收验证码的邮箱一致 |
| `userName` | string | 是 | 用户名 |
| `password` | string | 是 | **明文**密码 |
| `verifyCode` | string | 是 | 收到的 6 位邮箱验证码 |
| `sessionToken` | string | 是 | send_code 返回的令牌 |

- **响应**：`data` 为 `null`，以 `code`/`msg` 判断结果。

```json
{ "code": 200, "data": null, "msg": "注册成功", "timestamp": 1754912345678 }
```

> 失败可能返回：`204` 验证码错、`206` 会话过期、`207` 邮箱不一致、`3` 操作频繁；邮箱重复注册由数据库唯一索引拦截。

### 3.4 用户登录

- **`POST /api/v1/pri/user/login`**
- 鉴权：🟢 免登录
- **请求体**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `email` | string | 是 | 邮箱 |
| `password` | string | 是 | **明文**密码 |

- **响应 `data`**：`string`，即完整 token（含 `hajimi` 前缀）。

```json
{ "code": 200, "msg": "登录成功 已发放令牌 为期一周", "data": "hajimieyJhbGciOiJIUzI1NiJ9..." }
```

> 账号或密码错误返回 `code 208`。

---

## 四、航班搜索模块

两个搜索接口**请求体相同、响应结构不同**。优先用 4.1；4.1 内部异常会自动降级。

**公共请求体 `FlightSearchRequest`**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deptCity` | string | 是 | 出发城市，如 `北京` |
| `arrCity` | string | 是 | 到达城市，如 `上海` |
| `flightDate` | string | 是 | `yyyy-MM-dd` |
| `sortType` | int | 否 | `1`=起飞时间早→晚（默认），`2`=价格低→高 |

### 4.1 高性能搜索（推荐）

- **`POST /api/v1/pri/flight/search`**
- 鉴权：🟡 可选登录
- 描述：ES 检索 + Redis 实时缝合库存。
- **响应 `data`**：`FlightSearchVO[]`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 航班ID（预订时用） |
| `flightNo` | string | 航班号 |
| `deptCity` / `arrCity` | string | 出发/到达城市 |
| `flightDate` | date | 航班日期 |
| `totalPrice` | decimal | 总价 |
| `firstDeptTime` | datetime | 首段起飞时间 |
| `lastArrTime` | datetime | 末段到达时间 |
| `availableSeats` | int | **实时余票**；`-1` 表示「余票待查/维护中」，该航班暂不可购 |

```json
{ "code": 200, "msg": "航班查询成功",
  "data": [
    { "id": 888, "flightNo": "HA1001", "deptCity": "北京", "arrCity": "上海",
      "flightDate": "2026-08-20", "totalPrice": 1200.00,
      "firstDeptTime": "2026-08-20T08:00:00", "lastArrTime": "2026-08-20T10:15:00",
      "availableSeats": 50 } ] }
```

### 4.2 数据库降级搜索（兜底）

- **`POST /api/v1/pri/flight/search_flight`**
- 鉴权：🟡 可选登录
- 描述：直连 MySQL，用于中间件不可用时。**不含实时余票**。
- **响应 `data`**：`Flight[]`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 航班ID |
| `flightNo` | string | 航班号 |
| `deptCity` / `arrCity` | string | 出发/到达城市 |
| `flightDate` | date | 航班日期 |
| `deptTime` / `arrTime` | datetime | 起飞/到达时间 |
| `totalPrice` | decimal | 总价 |

> 注意字段名与 4.1 不同（`deptTime/arrTime` vs `firstDeptTime/lastArrTime`），且无 `availableSeats`。

---

## 五、预订模块

### 5.1 抢票预订

- **`POST /api/v1/pri/flight/booking`**
- 鉴权：🔴 必须登录
- 描述：原子完成防重、扣库存、占座，生成 **15 分钟** 待支付订单。
- **请求体 `BookingRequest`**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `flightId` | long | 是 | 航班ID |
| `userId` | long | 是 | **必须与 token 中的 `id` 一致**，否则 `code 4`（见 [1.2](#12-鉴权) 如何取 userId） |
| `seatPrefer` | string | 是 | 座位偏好：`window` 靠窗 / `aisle` 过道 / `middle` 中间 |

- **响应 `data`**：`TicketOrderVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `orderId` | long | **订单主键ID**，后续支付/取消/退款/查详情都用它 |
| `orderNo` | string | 订单号 `Hakimi-{orderId}` |
| `flightId` | long | 航班ID |
| `passengerName` | string | 乘客名 |
| `exactSeat` | string | 分配到的物理座位，如 `12A` |
| `totalPrice` | decimal | 金额 |
| `status` | string | 恒为 `UNPAID` |
| `isFinished` | int | 行程是否结束，`0`=未结束 |
| `createdAt` | datetime | 下单时间 |

```json
{ "code": 200, "msg": "预订成功，请在15分钟内完成支付",
  "data": { "orderId": 1937284756000123456, "orderNo": "Hakimi-1937284756000123456",
    "flightId": 888, "passengerName": "Edison", "exactSeat": "12A",
    "totalPrice": 1200.00, "status": "UNPAID", "isFinished": 0,
    "createdAt": "2026-08-11T19:30:00" } }
```

> 失败：`301` 已购此航班、`302` 售罄、`303` 航班未就绪、`1` 服务繁忙。

> ⚠️ **提示后端**：`userId` 已可从 token 解析，请求体再传一遍属冗余。若后端后续去掉该字段，本文档会同步更新。

---

## 六、订单与支付模块

### 6.1 发起支付 【核心·必读】

- **`POST /api/v1/pri/order/pay?order_id={orderId}`**
- 鉴权：🔴 必须登录
- **请求参数**（Query）：`order_id` (long) — 预订返回的 `orderId`
- **响应**：`JsonData<String>`，其中 **`data` 是一段支付宝 SDK 生成的 HTML `<form>` 代码**（自动提交表单），**不是 JSON 对象**。

```json
{ "code": 200, "msg": "已收到第三方支付平台响应",
  "data": "<form name='punchout_form' method='post' action='https://openapi-sandbox...'>...</form><script>document.forms[0].submit();</script>" }
```

#### 前端如何使用这段 HTML

把 `data` 注入页面即可触发跳转到支付宝收银台，例如：

```js
const res = await payOrder(orderId);        // 调 6.1
const div = document.createElement('div');
div.innerHTML = res.data;                    // 注入 HTML
document.body.appendChild(div);
div.querySelector('form').submit();          // 自动提交 → 跳转支付宝
// 或：const w = window.open(''); w.document.write(res.data);
```

#### 支付完成后的两条回流（务必理解）

支付宝在用户付款后有**两条独立**的通知，作用完全不同：

| | 同步跳转 `return-url` | 异步通知 `notify-url` |
|---|---|---|
| 对象 | **用户浏览器**（GET 重定向） | **支付宝服务器 → 后端**（POST） |
| 作用 | 把用户带回页面（**纯 UX**） | **真正确认支付**、扣库存、改订单状态 |
| 地址 | 后端配置 `alipay.return-url` | 后端 `/api/v1/pri/order/pay/callback` |
| 前端 | 落地页由前端提供 | 前端不参与 |

**要点 1 —— 同步跳转应落到「订单详情页」**：
支付成功后，支付宝会把用户浏览器重定向到后端配置的 `return-url`，并在其后拼接 `out_trade_no=Hakimi-{orderId}`、`trade_no`、`total_amount` 等 Query 参数。
👉 **交付约定**：`return-url` 应配置为前端的**订单详情页路由**（如 `https://<前端域名>/order/detail?orderId=...`），前端页面可从 URL 里的 `out_trade_no`（去掉 `Hakimi-` 前缀即 orderId）识别是哪笔订单。
> ⚠️ 该地址目前在后端 `application.yaml` 里是占位符 `https://www.baidu.com`。**交付时前后端需约定真实前端地址并由后端改配**。

**要点 2 —— 跳转 ≠ 支付已确认**：
真正把订单改成 `PAID` 的是异步回调，可能比同步跳转晚几百毫秒到数秒。因此前端落到详情页后，应**轮询 [6.6 订单详情](#66-订单详情)**，直到 `status` 由 `UNPAID` 变为 `PAID` 再展示「支付成功」；超时（如 10s）仍未变则提示「支付确认中，请稍后刷新」。

### 6.2 支付宝异步回调

- **`POST /api/v1/pri/order/pay/callback`**
- 鉴权：🟢（由支付宝网关调用）
- **前端不需要调用**。仅列出以说明支付确认的真正来源；此接口只对支付宝返回 `success`/`failure` 文本。

### 6.3 取消订单（未支付）

- **`POST /api/v1/pri/order/cancel`**
- 鉴权：🔴 必须登录
- 描述：仅可取消 `UNPAID` 订单，回滚库存与座位；幂等。
- **请求体**：`{ "orderId": 1937284756000123456 }`
- **响应 `data`**：`CancelOrderVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `orderId` | long | 订单ID |
| `currentStatus` | string | 恒为 `CANCELLED` |
| `cancelTime` | datetime | 取消时间，格式 `yyyy-MM-dd HH:mm:ss`（**注意非 ISO**） |
| `penaltyFee` | decimal | 扣费金额，未支付取消恒为 `0` |
| `displayMessage` | string | 友好提示文案 |

```json
{ "code": 200, "msg": "操作成功",
  "data": { "orderId": 1937284756000123456, "currentStatus": "CANCELLED",
    "cancelTime": "2026-08-11 19:40:00", "penaltyFee": 0,
    "displayMessage": "订单已免费取消，期待您下次预订" } }
```

> 订单不存在/已被取消/已支付返回 `code 304`。

### 6.4 退款（已支付）

- **`POST /api/v1/pri/order/refund`**
- 鉴权：🔴 必须登录
- 描述：仅可对 `PAID` 订单发起。**异步退款**——接口先把订单置为 `REFUNDING` 并受理，真正到账由后台完成，成功后通过 [WebSocket](#七实时通知websocket) 推送。
- **请求体**：`{ "orderId": 1937284756000123456 }`
- **响应 `data`**：`OrderRefundVO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `orderId` | long | 订单ID |
| `expectedRefundAmount` | decimal | 预计退还金额（距起飞不足 3 天扣 20% 手续费，否则全额） |
| `status` | string | 恒为 `REFUNDING` |
| `promptMessage` | string | 提示文案 |

```json
{ "code": 200, "msg": "操作成功",
  "data": { "orderId": 1937284756000123456, "expectedRefundAmount": 960.00,
    "status": "REFUNDING",
    "promptMessage": "退款申请已受理！系统正在向支付宝发起退款，预计1-3个工作日内原路退回。" } }
```

> 非 `PAID` 订单返回 `306`；重复/无效退款返回 `305`。
> 前端拿到 `REFUNDING` 后，可结合 [6.6 详情](#66-订单详情) 轮询或等 [WebSocket](#七实时通知websocket) 通知，直至变 `REFUNDED`。

### 6.5 订单列表

- **`GET /api/v1/pri/order/list`**
- 鉴权：🔴 必须登录
- **请求参数**（Query，可选）：`status` — 按状态筛选，不传查全部

| 取值 | 含义 |
|---|---|
| `UNPAID` | 待支付 |
| `PAID` | 已支付 |
| `CANCELLED` | 已取消 |
| `REFUNDING` | 退款中 |
| `REFUNDED` | 已退款 |

- **响应 `data`**：`OrderVO[]`，按下单时间**倒序**。字段见 [6.6](#66-订单详情)。

```
GET /api/v1/pri/order/list           # 全部
GET /api/v1/pri/order/list?status=PAID
```

### 6.6 订单详情

- **`GET /api/v1/pri/order/detail?order_id={orderId}`**
- 鉴权：🔴 必须登录（且只能查本人订单）
- **请求参数**（Query）：`order_id` (long)
- **响应 `data`**：`OrderVO`（列表与详情共用同一结构）

| 字段 | 类型 | 说明 |
|---|---|---|
| `orderId` | long | 订单主键ID |
| `orderNo` | string | 订单号 `Hakimi-{orderId}` |
| `status` | string | 订单状态（见 [第八章](#八订单状态机)） |
| `totalPrice` | decimal | 订单金额 |
| `exactSeat` | string | 座位号，如 `12B` |
| `seatOffset` | int | 座位偏移量（内部值，前端可忽略） |
| `passengerName` | string | 乘客名 |
| `payTradeNo` | string | 支付流水号，未支付时为 `null` |
| `createdAt` | datetime | 下单时间 |
| `flightId` | long | 航班ID |
| `flightNo` | string | 航班号 |
| `deptCity` / `arrCity` | string | 出发/到达城市 |
| `flightDate` | date | 航班日期 |
| `deptTime` / `arrTime` | datetime | 起飞/到达时间 |

```json
{ "code": 200, "msg": "查询成功",
  "data": {
    "orderId": 1937284756000123456, "orderNo": "Hakimi-1937284756000123456",
    "status": "PAID", "totalPrice": 1200.00, "exactSeat": "12B", "seatOffset": 67,
    "passengerName": "Edison", "payTradeNo": "2026081122001...", "createdAt": "2026-08-11T19:30:00",
    "flightId": 888, "flightNo": "HA1001", "deptCity": "北京", "arrCity": "上海",
    "flightDate": "2026-08-20", "deptTime": "2026-08-20T08:00:00", "arrTime": "2026-08-20T10:15:00" } }
```

> 订单不存在或非本人返回 `code 304`（不区分，避免泄露他人订单存在性）。

---

## 七、实时通知（WebSocket）

- **连接**：`ws://<host>:8080/ws/notifications/{userId}`
  - `userId` 为登录用户ID（从 token 解析，见 [1.2](#12-鉴权)），登录后建立连接。
- **协议**：服务端单向推送**文本消息**，内容为 JSON 字符串。
- **消息格式**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | 通知类型，目前 `REFUND_SUCCESS`（退款成功） |
| `orderId` | long | 关联订单ID |
| `content` | string | 展示文案 |

```json
{ "type": "REFUND_SUCCESS", "orderId": 1937284756000123456, "content": "您的订单已退款成功，金额已原路返回。" }
```

- 前端示例：
```js
const ws = new WebSocket(`ws://localhost:8080/ws/notifications/${userId}`);
ws.onmessage = (e) => { const n = JSON.parse(e.data); /* 弹 toast + 刷新该订单 */ };
```

> MVP 说明：当前 WS 握手仅凭路径中的 userId，未做 token 鉴权，后续会加固；断线重连由前端处理。

---

## 八、订单状态机

```
下单
  │
  ▼
UNPAID ──支付成功(异步回调)──▶ PAID ──申请退款──▶ REFUNDING ──到账──▶ REFUNDED
  │                             
  ├──用户主动取消───▶ CANCELLED
  └──15分钟超时未支付─▶ CANCELLED
```

| 状态 | 含义 | 可用操作 |
|---|---|---|
| `UNPAID` | 待支付（15 分钟时限） | 支付(6.1)、取消(6.3) |
| `PAID` | 已支付 | 退款(6.4) |
| `CANCELLED` | 已取消（主动或超时） | — |
| `REFUNDING` | 退款处理中 | 等待（轮询/WS） |
| `REFUNDED` | 已退款 | — |

---

## 九、B 端故障停售接口

> 以下接口位于 `/dev/*`，仅供内部运维和本地演示使用，不纳入前端交付范围。

### 9.1 查询航班销售状态

- **`GET /dev/flight/{flightId}/sale-state`**
- 鉴权：内部运维接口
- 用途：查询航班是否因 Redis 航班路由或航段库存数据异常而被自动暂停销售。
- **响应体**：

```json
{
  "flightId": 42,
  "salePaused": true,
  "reason": "STOCK_MISSING:123"
}
```

`reason` 可能为 `ROUTE_MISSING`、`STOCK_MISSING:{segmentId}` 或 `STOCK_INVALID:{segmentId}`。正常销售时 `salePaused` 为 `false`，`reason` 为 `null`。

### 9.2 校验并恢复航班销售

- **`POST /dev/flight/{flightId}/sale-resume`**
- 鉴权：内部运维接口
- 前置操作：修复数据后执行 `GET /dev/flight/sync`，重建 Redis 路由和库存缓存。
- 服务端会确认 MySQL 航班存在、Redis 路由非空且所有航段库存是有效的非负整数；校验成功后删除停售屏障。
- **成功响应体**：与 9.1 相同，`salePaused` 为 `false`。
- 校验失败时返回航班数据异常（错误码 `303`），停售屏障保留。

---

> 📬 接口疑问联系后端：codeonstring1024@gmail.com
