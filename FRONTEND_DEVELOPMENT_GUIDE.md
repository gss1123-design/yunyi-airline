# 哈基米航空 · 前端开发指导手册（v1）

> 本手册面向**前端开发者 / 前端 AI Agent**，讲清楚**怎么搭、怎么组织代码、v1 做到哪、怎么和后端联调**。
>
> 三份交付文档分工：
> - **`APIs.md`** —— 接口契约（路径 / 请求 / 响应 / VO 字段 / 错误码），**唯一权威**，凡涉及字段一律以它为准。
> - **`README.md`** —— 后端架构与原理（了解背景用）。
> - **本手册** —— 前端怎么落地。
>
> 👉 动手前请先读 **`APIs.md` 第一章（基础约定）**，尤其是 §1.1 响应体、§1.2 鉴权、§1.4 错误码表。

---

## 目录

- [1. 技术栈](#1-技术栈)
- [2. v1 范围与非目标](#2-v1-范围与非目标)
- [3. 项目初始化与目录结构](#3-项目初始化与目录结构)
- [4. 联调网络：BaseURL 与 Vite Proxy](#4-联调网络baseurl-与-vite-proxy)
- [5. API 层统一封装（axios）](#5-api-层统一封装axios)
- [6. Token 策略（Pinia + localStorage）](#6-token-策略pinia--localstorage)
- [7. 路由与页面清单](#7-路由与页面清单)
- [8. 核心业务流程（端到端）](#8-核心业务流程端到端)
- [9. 轮询工具（替代 v1 的 WebSocket）](#9-轮询工具替代-v1-的-websocket)
- [10. 错误处理与 UX 约定](#10-错误处理与-ux-约定)
- [11. 工作方式要求（给前端 Agent 的工作约定）](#11-工作方式要求给前端-agent-的工作约定)
- [12. 交付物与后端协作清单](#12-交付物与后端协作清单)

---

## 1. 技术栈

| 技术 | 用途 | 说明 |
|---|---|---|
| **Vue 3** | 视图框架 | 统一用 `<script setup>` + Composition API |
| **Vite** | 构建 / dev server | 用它的 proxy 解决联调跨域 |
| **Pinia** | 状态管理 | 存 token、用户信息 |
| **Vue Router** | 路由 | 页面导航 + 登录守卫 |
| **axios** | HTTP 客户端 | 统一封装在 `src/api` 层 |

- **Node 版本**：≥ 18（Vite 5 要求）。
- **包管理**：npm / pnpm 皆可，本手册示例用 npm。
- UI 组件库不强制（Element Plus / Naive UI / 纯手写均可），v1 以跑通流程为先。

---

## 2. v1 范围与非目标

**v1 要做（跑通完整可交互闭环）：**

1. 注册（三步：图形码 → 邮箱码 → 注册）、登录
2. 航班搜索 + 结果列表
3. 抢票预订（下单）
4. 支付（**当前页跳转支付宝 + return-url 跳回**）
5. 订单列表 / 订单详情
6. 取消未支付订单
7. 退款（发起 + **轮询**订单状态到已退款）

**v1 暂不做（记为 v2）：**

- ❌ **WebSocket 实时通知** —— v1 一律用**轮询 `/order/detail`** 替代（支付、退款的状态更新都靠轮询，见 [§9](#9-轮询工具替代-v1-的-websocket)）。
- ❌ 订单列表分页（后端 v1 全量返回，前端不做分页）。
- ❌ 可视化选座（只传座位偏好 `window/aisle/middle`，座位由后端分配）。

> 📌 **克制原则**：不要越界实现「暂不做」的功能，也不要臆造 APIs.md 里没有的接口/字段。

---

## 3. 项目初始化与目录结构

前端是**独立仓库 / 独立目录**（与后端分离，自跑 dev server，默认端口 5173）。

```bash
npm create vite@latest hakimi-air-frontend -- --template vue
cd hakimi-air-frontend
npm i
npm i vue-router pinia axios
```

**标准目录结构（务必遵守）：**

```
src/
├── api/            # ★ 所有后端调用只能在这里，组件里禁止直接 import axios
│   ├── http.js     # axios 实例 + 拦截器（统一封装）
│   ├── user.js     # 用户相关接口
│   ├── flight.js   # 搜索 / 预订接口
│   └── order.js    # 支付 / 订单 / 退款接口
├── stores/         # Pinia
│   └── auth.js     # token + 用户信息
├── router/
│   └── index.js    # 路由 + 登录守卫
├── views/          # 页面级组件
│   ├── Login.vue  Register.vue  Search.vue
│   ├── Booking.vue  OrderList.vue  OrderDetail.vue
├── components/     # 复用组件（航班卡片、倒计时、状态标签…）
├── utils/          # 工具
│   ├── jwt.js      # 解析 JWT 拿 userId
│   └── poll.js     # 轮询订单状态
├── composables/    # 可选：useXxx 组合式函数
├── App.vue
└── main.js
```

`main.js` 挂载 Pinia、Router，并在启动时恢复登录态：

```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
app.use(createPinia())
app.use(router)
useAuthStore().restore()   // 刷新后从 localStorage 恢复 token/userInfo
app.mount('#app')
```

---

## 4. 联调网络：BaseURL 与 Vite Proxy

**用 Vite proxy 解决跨域**，前端不直连后端域名，避免任何 CORS 问题。

- axios 的 `baseURL` 设为相对路径 **`/api/v1`**。
- Vite dev server 把 `/api/v1/**` 转发到后端 `http://127.0.0.1:8080`。
- 浏览器视角是同源（都在 `localhost:5173`），**不触发 CORS**。

`vite.config.js`：

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 所有 /api/v1 开头的请求 → 转发到后端；后端本身就在 /api/v1 下，无需 rewrite
      '/api/v1': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
```

### ⚠️ 路径拼接：不要出现双 `/api/v1`

APIs.md 里每个接口标的是**完整绝对路径**（如 `/api/v1/pri/user/login`），而我们的 axios `baseURL` **已经包含 `/api/v1`**。所以在代码里调用时**只写 `/api/v1` 之后的部分**：

| | 值 |
|---|---|
| axios `baseURL` | `/api/v1` |
| ✅ 代码里写 | `/pri/user/login` |
| ❌ 错误写法 | `/api/v1/pri/user/login`（会变成 `/api/v1/api/v1/...`）|
| 最终请求（经 proxy） | `http://127.0.0.1:8080/api/v1/pri/user/login` |

> 规律：所有前端接口都在 `/api/v1/pri/...` 下 → 代码里一律以 **`/pri/...`** 开头。
> APIs.md 中写的 `http://127.0.0.1:8080/api/v1` 是**后端真实地址**；用了 proxy 之后前端写相对的 `/api/v1` 即可，二者等价。

---

## 5. API 层统一封装（axios）

**铁律：所有后端请求都经过 `src/api/*`，组件里绝不直接 `import axios`。**

### 5.1 axios 实例 + 拦截器 `src/api/http.js`

响应拦截器统一做三件事：解包 `JsonData`、按 `code===200` 判成功、失败分流（`-1` 踢回登录 / 其余抛出供 toast）。

```js
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

export class BizError extends Error {
  constructor(code, msg) { super(msg); this.code = code; this.name = 'BizError' }
}

const http = axios.create({
  baseURL: '/api/v1',   // 经 Vite proxy 转发；调用时写 '/pri/...'
  timeout: 15000,
})

// 请求拦截器：自动注入 token 头（原样，含 hajimi 前缀）
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) config.headers.token = auth.token
  return config
})

// 响应拦截器：解包 + 判码
http.interceptors.response.use(
  (response) => {
    const res = response.data            // { code, data, msg, timestamp }
    if (res.code === 200) return res.data // 成功：直接把业务 data 交回调用方
    if (res.code === -1) {               // 登录态失效 / 未知异常
      useAuthStore().clearToken()
      router.replace('/login')
    }
    return Promise.reject(new BizError(res.code, res.msg)) // 业务错误交调用方 catch
  },
  () => Promise.reject(new BizError(-1, '网络异常，请稍后重试')),
)

export default http
```

> 封装后：**api 函数直接 resolve 出业务 `data`**，失败走 `catch(e)`，`e.code` / `e.msg` 可用于提示。
> 注意支付接口 `data` 是一段 **HTML 字符串**（不是对象），照常返回，由调用方处理（见 §8.5 支付）。

### 5.2 接口模块（一函数一端点）—— 以 `src/api/order.js` 为例

```js
import http from './http'

// 发起支付：返回支付宝表单 HTML 字符串（APIs.md §6.1）
export const payOrder = (orderId) =>
  http.post('/pri/order/pay', null, { params: { order_id: orderId } })

// 取消未支付订单（§6.3）
export const cancelOrder = (orderId) => http.post('/pri/order/cancel', { orderId })

// 申请退款（§6.4）
export const refundOrder = (orderId) => http.post('/pri/order/refund', { orderId })

// 我的订单列表，status 可选（§6.5）
export const listOrders = (status) =>
  http.get('/pri/order/list', { params: status ? { status } : {} })

// 订单详情（§6.6）
export const getOrderDetail = (orderId) =>
  http.get('/pri/order/detail', { params: { order_id: orderId } })
```

`user.js` / `flight.js` 同理，按 APIs.md 第三、四、五章逐个封装（captcha / send_code / register / login / search / search_flight / booking）。

---

## 6. Token 策略（Pinia + localStorage）

- 登录接口返回的 `data` 就是**完整 token 字符串**（含 `hajimi` 前缀）→ **原样存**、**原样放 `token` 头**。
- 存 Pinia（响应式）+ localStorage（刷新不丢）。
- 预订接口请求体要 `userId`，从 token 解码得到（APIs.md §1.2）。

`src/stores/auth.js`：

```js
import { defineStore } from 'pinia'
import { decodeJwt } from '@/utils/jwt'

const TOKEN_KEY = 'hakimi_token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: null,                       // { id, name, head_img }
  }),
  getters: {
    isLogin: (s) => !!s.token,
    userId: (s) => s.userInfo?.id ?? null,
  },
  actions: {
    setToken(token) {
      this.token = token
      localStorage.setItem(TOKEN_KEY, token)
      this.userInfo = decodeJwt(token)    // 解析出 id/name
    },
    clearToken() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
    },
    restore() { if (this.token) this.userInfo = decodeJwt(this.token) },
  },
})
```

`src/utils/jwt.js`（去 `hajimi` 前缀 + base64url 解 payload）：

```js
export function decodeJwt(token) {
  try {
    const raw = token.replace(/^hajimi/, '')
    const payload = raw.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decodeURIComponent(escape(json)))  // { id, name, head_img }
  } catch {
    return null
  }
}
```

---

## 7. 路由与页面清单

| 路由 | 页面 | 登录 | 说明 |
|---|---|---|---|
| `/login` | 登录 | 否 | 登录成功存 token → 跳首页 |
| `/register` | 注册 | 否 | 三步注册（图形码/邮箱码/提交） |
| `/` | 搜索 + 结果列表 | 是 | 搜索表单 + 航班卡片列表 |
| `/booking/:flightId` | 预订确认 | 是 | 选座位偏好 → 下单 → 去支付 |
| `/orders` | 订单列表 | 是 | 我的订单，可按状态筛选 |
| `/order/detail` | 订单详情 | 是 | 单订单详情；**兼作支付回跳落地页** |

> 搜索页也可拆成「搜索」+「结果列表」两个页面，看实现方便；预订也可做成结果卡片上的弹窗，不强制独立路由。

`src/router/index.js`（含登录守卫）：

```js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/Register.vue'), meta: { public: true } },
  { path: '/', component: () => import('@/views/Search.vue') },
  { path: '/booking/:flightId', component: () => import('@/views/Booking.vue') },
  { path: '/orders', component: () => import('@/views/OrderList.vue') },
  { path: '/order/detail', component: () => import('@/views/OrderDetail.vue') },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

export default router
```

---

## 8. 核心业务流程（端到端）

> 下面每条只讲「调哪些接口 + 关键 UI 状态」，**字段/请求体/响应结构一律看 APIs.md 对应章节**。

### 8.1 注册（三步，APIs.md §3）

1. 进页面 → `GET /pri/user/captcha`，拿 `{uuid, imgBase64}`，`imgBase64` 直接塞 `<img :src>`；`uuid` 存着当 `captchaKey`。
2. 用户填邮箱 + 图形码 → `POST /pri/user/send_code {email, picCode, captchaKey}`，拿 `{sessionToken}` 存着。
3. 用户填邮箱验证码 + 用户名 + 密码（**明文**）→ `POST /pri/user/register {email, userName, password, verifyCode, sessionToken}`。
   - 图形码可点击刷新（重新调 captcha）；发码有 60s 冷却（`code 2`）。

### 8.2 登录（§3.4）

- `POST /pri/user/login {email, password}` → `data` 是 token 字符串 → `auth.setToken(token)` → 跳 `redirect` 或 `/`。

### 8.3 搜索（§4）

- `POST /pri/flight/search {deptCity, arrCity, flightDate, sortType}`（`sortType` 1=时间 2=价格）→ `FlightSearchVO[]`。
- **`availableSeats === -1`**：表示「余票待查/维护中」→ 该卡片**置灰、禁止预订**。
- `/pri/flight/search_flight` 是降级接口，返回结构不同（无实时余票），v1 一般只用 `search`。

### 8.4 预订（§5.1）

- 需要 `userId`：从 `auth.userId`（token 解码而来）取。
- `POST /pri/flight/booking {flightId, userId, seatPrefer}`（`seatPrefer` ∈ `window/aisle/middle`）。
- 返回 `TicketOrderVO`，含 **`orderId`（数字）**、`orderNo`、`exactSeat`（如 `12A`）、`status=UNPAID`。
- 下单成功 UI：展示座位号 + **15 分钟支付倒计时**，提供「去支付」按钮（带上 `orderId`）。
- ⚠️ 后端订单是**异步落库**的：下单成功后**别立刻打 `/order/detail`**（可能短暂 `304`），用 booking 返回的 VO 渲染即可。

### 8.5 支付（当前页跳转 + return-url）

> v1 采用「当前页跳转支付宝，支付后由 return-url 跳回」。

1. 点「去支付」→ `payOrder(orderId)` 拿到**支付宝表单 HTML 字符串**。
2. 注入当前页并自动提交 → 浏览器跳转到支付宝收银台：

```js
import { payOrder } from '@/api/order'

async function goPay(orderId) {
  const html = await payOrder(orderId)     // data 是 <form>...</form> + 自动提交脚本
  const div = document.createElement('div')
  div.innerHTML = html
  document.body.appendChild(div)
  div.querySelector('form').submit()       // 页面跳走
}
```

3. 用户在支付宝付款后，支付宝把浏览器**重定向到后端配置的 `return-url`**，并在 URL 后拼接 `out_trade_no=Hakimi-{orderId}`、`trade_no`、`total_amount`。
4. **`return-url` 应指向前端订单详情页**（见 [§12 后端协作项](#12-交付物与后端协作清单)）：`http://127.0.0.1:5173/order/detail`。
5. 详情页从 URL 取 `out_trade_no` → 去掉 `Hakimi-` 得 `orderId` → **轮询 `/order/detail` 直到 `status=PAID`** 再显示「支付成功」（异步回调有延迟，跳回 ≠ 已确认）。

```js
// OrderDetail.vue —— 兼作支付回跳落地
import { useRoute } from 'vue-router'
import { pollOrderDetail } from '@/utils/poll'

const route = useRoute()
const orderId =
  route.query.order_id ||
  (route.query.out_trade_no || '').replace('Hakimi-', '')   // 支付宝跳回时

if (route.query.out_trade_no) {
  pollOrderDetail(orderId, 'PAID', { interval: 2000, timeout: 30000 })
    .then(() => { /* 显示支付成功 */ })
    .catch(() => { /* 提示“支付确认中，请稍后刷新” */ })
}
```

> 用户中途放弃：订单停在 `UNPAID`，15 分钟后后端自动取消（→ `CANCELLED`）。

### 8.6 订单列表 / 详情（§6.5 / §6.6）

- 列表：`listOrders(status?)` → `OrderVO[]`，按状态 tab 切换传 `status`（`UNPAID/PAID/CANCELLED/REFUNDING/REFUNDED`）。
- 详情：`getOrderDetail(orderId)` → `OrderVO`（含航班展示字段 + `exactSeat`），非本人/不存在返回 `304`。

### 8.7 取消（§6.3，仅 UNPAID）

- `cancelOrder(orderId)` → `CancelOrderVO`（`currentStatus=CANCELLED`，`penaltyFee=0`）→ 刷新列表/详情。

### 8.8 退款（§6.4，仅 PAID；v1 用轮询）

1. `refundOrder(orderId)` → `OrderRefundVO`（`status=REFUNDING`，`expectedRefundAmount` 可展示）。
2. 拿到受理后 → **轮询 `/order/detail` 直到 `status=REFUNDED`**，再提示「退款成功」。

```js
await refundOrder(orderId)
await pollOrderDetail(orderId, 'REFUNDED', { interval: 3000, timeout: 60000 })
// 到达 REFUNDED → 提示成功
```

---

## 9. 轮询工具（替代 v1 的 WebSocket）

后端有 WebSocket 推送退款结果，但 **v1 暂不接**；支付、退款的状态更新统一用轮询。

`src/utils/poll.js`：

```js
import { getOrderDetail } from '@/api/order'

/**
 * 轮询订单状态，直到达到 targetStatus 或超时。
 * 支付后等 UNPAID→PAID；退款后等 REFUNDING→REFUNDED。
 * @returns Promise<OrderVO> 命中即 resolve；超时 reject(Error('POLL_TIMEOUT'))
 */
export function pollOrderDetail(orderId, targetStatus, { interval = 2000, timeout = 60000 } = {}) {
  const deadline = Date.now() + timeout
  return new Promise((resolve, reject) => {
    const tick = async () => {
      try {
        const order = await getOrderDetail(orderId)
        if (order.status === targetStatus) return resolve(order)
      } catch (e) {
        if (Date.now() >= deadline) return reject(e)   // 出错也重试到超时
      }
      if (Date.now() >= deadline) return reject(new Error('POLL_TIMEOUT'))
      setTimeout(tick, interval)
    }
    tick()
  })
}
```

> v2 接 WebSocket 时（APIs.md §7），把「轮询」换成 `ws://127.0.0.1:8080/ws/notifications/{userId}` 的 `onmessage` 即可，业务层无需大改。

---

## 10. 错误处理与 UX 约定

- **判成功**：只认 `code === 200`（拦截器已处理）；HTTP 状态一律 200，**别用 HTTP 状态判断**。
- **`code === -1`**：登录态失效/系统异常 → 拦截器已清 token 并跳 `/login`。
- **业务错误**：`catch(e)` 里 `e.msg` 直接 toast。常见码（完整见 APIs.md §1.4）：

| code | 场景 | 建议交互 |
|---|---|---|
| `301` | 已购买过此航班 | toast，禁用该航班预订 |
| `302` | 机票已售罄 | toast，刷新余票 |
| `304` | 订单不存在/已超时 | toast，回订单列表 |
| `306` | 订单状态不允许退款 | toast |
| `2` | 验证码发送过于频繁 | 倒计时禁用发码按钮 |

- **日期时间**：默认 ISO-8601（`2026-08-20T08:00:00`）；**例外** `CancelOrderVO.cancelTime` 是 `yyyy-MM-dd HH:mm:ss`（见 APIs.md §1.3）。格式化展示时注意兼容。
- **空态**：搜索无结果、订单列表为空要有空状态占位。

---

## 11. 工作方式要求（给前端 Agent 的工作约定）

1. **先规划，后写码**：动手前先产出「页面/组件/store/api 模块划分 + 路由表 + 数据流」的简要规划，确认后再实现。
2. **遵守标准目录**：严格按 [§3](#3-项目初始化与目录结构) 的结构落文件。
3. **API 层唯一入口**：所有后端调用只写在 `src/api/*`，**组件内禁止直接 `import axios`**；组件通过 api 函数拿数据。
4. **契约以 APIs.md 为准**：不臆造字段、路径、错误码；拿不准先查 APIs.md，查不到就停下来问，别猜。
5. **每块讲解**：每完成一个模块（如 auth store、支付流程），简述「做了什么、为什么这么做、涉及哪些接口」。
6. **克制在 v1 范围**：不实现 [§2](#2-v1-范围与非目标) 里「暂不做」的功能。
7. **路径别双前缀**：牢记 axios baseURL 已含 `/api/v1`，调用写 `/pri/...`（见 [§4](#4-联调网络baseurl-与-vite-proxy)）。

---

## 12. 交付物与后端协作清单

**交给前端的三份文档**：`README.md`、`APIs.md`、`FRONTEND_DEVELOPMENT_GUIDE.md`（本文件）。

**联调前需要后端配合的事项：**

1. **⚠️ 改 `return-url`（支付闭环必须）**：后端 `application.yaml` 的 `alipay.return-url` 现在是占位符 `https://www.baidu.com`，需改成前端订单详情页地址，例如 `http://127.0.0.1:5173/order/detail`。否则支付完跳不回前端。
2. **起服务**：后端 + MySQL / Redis / RabbitMQ / Elasticsearch 都要在本机（或本机可达）跑起来。
3. **造数据**：先调后端 B 端接口生成可搜索的航班：`GET /dev/init?days=1` 再 `GET /dev/flight/sync`（详见 README）。
4. **支付回调需公网可达**：支付宝异步回调（真正确认支付的一步）打的是后端 `notify-url`，本机需用 cpolar 等内网穿透暴露后端（后端已配 `CPOLAR_DOMAIN`）。纯 UI 联调阶段可暂不处理，订单会停在 `UNPAID`。

---

> 📬 接口/联调问题联系后端：codeonstring1024@gmail.com
> 🐱 哈基米航空 · 前端 v1 加油！
