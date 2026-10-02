(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const storage = window.localStorage;
  const HISTORY_LIMIT = 12;
  let requestHistory = [];
  let captchaSession = "";
  let toastTimer = 0;

  function safeRead(key, fallback) {
    try { return storage.getItem(key) || fallback; } catch (_) { return fallback; }
  }

  function apiBase() {
    let base = $("apiBase").value.trim() || "/api/v1";
    base = base.replace(/\/+$/, "");
    return /\/api\/v1$/i.test(base) ? base : base + "/api/v1";
  }

  function apiUrl(path, query) {
    const root = apiBase();
    const joined = root + (path.charAt(0) === "/" ? path : "/" + path);
    const url = new URL(joined, window.location.href);
    Object.entries(query || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && String(value) !== "") url.searchParams.set(key, value);
    });
    return url;
  }

  function devUrl(path, query) {
    const base = new URL(apiBase(), window.location.href);
    base.pathname = base.pathname.replace(/\/api\/v1\/?$/i, "");
    const cleanPath = path.charAt(0) === "/" ? path : "/" + path;
    base.pathname = (base.pathname.replace(/\/+$/, "") + cleanPath).replace(/\/{2,}/g, "/");
    Object.entries(query || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && String(value) !== "") base.searchParams.set(key, value);
    });
    return base;
  }

  function tokenValue() {
    return $("tokenInput").value.trim();
  }

  function parseJsonPreservingLongs(text) {
    const normalized = text.replace(/(:\s*)(-?\d{16,})(?=\s*[,}\]])/g, (match, prefix, digits) => {
      return Number.isSafeInteger(Number(digits)) ? match : prefix + '"' + digits + '"';
    });
    return JSON.parse(normalized);
  }

  function tokenClaims(token) {
    try {
      const compact = token.replace(/^hajimi/i, "");
      const payload = compact.split(".")[1];
      if (!payload) return null;
      const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
      const decoded = decodeURIComponent(Array.prototype.map.call(atob(base64), (char) =>
        "%" + ("00" + char.charCodeAt(0).toString(16)).slice(-2)).join(""));
      return parseJsonPreservingLongs(decoded);
    } catch (_) { return null; }
  }

  function updateAuthUI() {
    const token = tokenValue();
    const claims = token ? tokenClaims(token) : null;
    const userId = claims && claims.id !== undefined ? claims.id : "";
    const name = claims && (claims.name || claims.userName) ? (claims.name || claims.userName) : "";
    $("sideUser").textContent = token ? (name || "已登录") : "游客模式";
    $("sideUserId").textContent = token ? (userId ? "ID " + userId : "Token 已设置") : "尚未登录";
    $("tokenDot").classList.toggle("online", Boolean(token));
    $("tokenMeta").textContent = token
      ? (claims ? "JWT 已解析 · 用户 " + (name || "—") + " · ID " + (userId || "—") : "Token 已保存，但无法解析 JWT 载荷")
      : "未检测到 Token";
    $("tokenMeta").classList.toggle("valid", Boolean(token && claims));
    $("bookingUserId").value = userId || "";
  }

  function toast(message, isError) {
    const element = $("toast");
    element.textContent = message;
    element.classList.toggle("error", Boolean(isError));
    element.classList.add("show");
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => element.classList.remove("show"), 3000);
  }

  function pretty(value) {
    if (typeof value === "string") {
      try { return JSON.stringify(JSON.parse(value), null, 2); } catch (_) { return value; }
    }
    try { return JSON.stringify(value, null, 2); } catch (_) { return String(value); }
  }

  function responseSummary(result) {
    if (result && typeof result === "object") return result.msg || result.message || ("HTTP " + (result.httpStatus || ""));
    return String(result || "完成");
  }

  function paintInspector(record) {
    $("requestMethod").textContent = record.method;
    $("requestMethod").className = "method-pill " + record.method;
    $("requestUrl").textContent = record.url;
    $("requestUrl").title = record.url;
    $("requestTime").textContent = record.time;
    $("requestPreview").textContent = pretty(record.request);
    $("responsePreview").textContent = pretty(record.response);
    $("responseStatus").textContent = record.statusLabel;
    $("responseStatus").style.color = record.ok ? "#4caf84" : "#e27676";
  }

  function paintHistory() {
    const root = $("historyList");
    root.replaceChildren();
    if (!requestHistory.length) {
      const empty = document.createElement("div");
      empty.className = "empty-state";
      empty.textContent = "还没有请求记录。选择上方模块开始操作。";
      root.appendChild(empty);
      return;
    }
    requestHistory.forEach((item) => {
      const row = document.createElement("div");
      row.className = "history-item";
      const method = document.createElement("span");
      method.className = "method-pill " + item.method;
      method.textContent = item.method;
      const name = document.createElement("span");
      name.className = "history-name";
      name.textContent = item.label;
      const url = document.createElement("code");
      url.className = "history-url";
      url.textContent = item.shortUrl;
      const status = document.createElement("span");
      status.className = "history-status " + (item.ok ? "ok" : "bad");
      status.textContent = item.statusLabel;
      row.append(method, name, url, status);
      row.addEventListener("click", () => paintInspector(item));
      row.style.cursor = "pointer";
      root.appendChild(row);
    });
  }

  async function sendRequest(options) {
    const method = (options.method || "GET").toUpperCase();
    const url = options.url instanceof URL ? options.url : apiUrl(options.path || "", options.query);
    const headers = { Accept: "application/json, text/plain, */*" };
    const token = options.noToken ? "" : tokenValue();
    if (token && options.auth !== false) headers.token = token;
    let body;
    let safeBody = options.body === undefined ? null : options.body;
    if (options.body !== undefined && method !== "GET" && method !== "HEAD") {
      headers["Content-Type"] = "application/json";
      body = typeof options.body === "string" ? options.body : JSON.stringify(options.body);
      if (options.hideBody) safeBody = { ...options.body, password: options.body.password ? "••••••" : undefined };
    }
    const startedAt = Date.now();
    const record = {
      label: options.label || method + " " + url.pathname,
      method,
      url: url.toString(),
      shortUrl: url.pathname + url.search,
      request: { headers: token ? { token: "••••••" } : {}, body: safeBody },
      response: { 状态: "请求中…" },
      statusLabel: "…",
      time: new Date().toLocaleTimeString("zh-CN", { hour12: false }),
      ok: false
    };
    paintInspector(record);
    try {
      const response = await fetch(url.toString(), { method, headers, body, cache: "no-store" });
      const text = await response.text();
    let result = text;
      try { result = text ? parseJsonPreservingLongs(text) : null; } catch (_) {}
      record.response = result;
      record.ok = response.ok && !(result && typeof result === "object" && result.code !== undefined && result.code !== 200);
      record.statusLabel = "HTTP " + response.status + (result && result.code !== undefined ? " · " + result.code : "");
      record.elapsed = Date.now() - startedAt;
      record.request = {
        headers: token ? { token: "••••••" } : {},
        body: safeBody
      };
      requestHistory.unshift(record);
      requestHistory = requestHistory.slice(0, HISTORY_LIMIT);
      paintInspector(record);
      paintHistory();
      if (!response.ok) toast("请求失败：HTTP " + response.status, true);
      else if (result && typeof result === "object" && result.code !== undefined && result.code !== 200) toast(result.msg || "业务请求失败", true);
      else toast(options.successMessage || "请求完成 · " + record.elapsed + " ms", false);
      return { response, result, record };
    } catch (error) {
      record.response = { error: error.message, hint: "请确认 API 地址和 Docker 后端状态。" };
      record.statusLabel = "NETWORK";
      record.ok = false;
      requestHistory.unshift(record);
      requestHistory = requestHistory.slice(0, HISTORY_LIMIT);
      paintInspector(record);
      paintHistory();
      toast("网络错误：请检查后端连接与 API Base URL", true);
      return { response: null, result: record.response, record };
    }
  }

  function goTo(name) {
    document.querySelectorAll(".panel").forEach((panel) => panel.classList.toggle("active", panel.id === "panel-" + name));
    document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.panel === name));
    const nav = document.querySelector('.nav-item[data-panel="' + name + '"]');
    $("pageCrumb").textContent = nav ? nav.textContent.trim() : name;
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function setApiStatus(online, label) {
    $("apiDot").classList.toggle("online", online);
    $("apiDot").classList.toggle("offline", !online);
    $("apiStatus").textContent = label;
  }

  async function checkBackend() {
    const result = await sendRequest({ label: "后端连接检查", method: "GET", url: devUrl("/dev/flight/1/sale-state"), auth: false });
    if (result.response && result.response.ok) setApiStatus(true, "后端已连接");
    else setApiStatus(false, "后端不可用");
  }

  function datePlus(days) {
    const date = new Date();
    date.setDate(date.getDate() + days);
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
  }

  function setSelectedOrder(order) {
    const root = $("selectedOrder");
    root.replaceChildren();
    root.classList.remove("empty-state", "compact");
    root.classList.add("selected-order", "has-data");
    const fields = [
      ["订单号", order.orderNo || order.orderId],
      ["状态", order.status],
      ["航班", order.flightNo || order.flightId],
      ["乘机人", order.passengerName],
      ["座位", order.exactSeat],
      ["金额", order.totalPrice]
    ];
    fields.forEach(([label, value]) => {
      if (value === undefined || value === null || value === "") return;
      const item = document.createElement("div");
      item.textContent = label;
      const strong = document.createElement("strong");
      strong.textContent = String(value);
      item.appendChild(strong);
      root.appendChild(item);
    });
  }

  function renderFlights(result) {
    const root = $("flightResults");
    root.replaceChildren();
    const data = result && result.data;
    const flights = Array.isArray(data) ? data : [];
    $("resultCount").textContent = flights.length ? flights.length + " 个结果" : "没有结果";
    if (!flights.length) {
      const empty = document.createElement("div");
      empty.className = "empty-state";
      empty.textContent = result && result.msg ? result.msg : "没有查到航班。可先运行开发数据初始化与同步。";
      root.appendChild(empty);
      return;
    }
    flights.forEach((flight) => {
      const card = document.createElement("article");
      card.className = "flight-card";
      const identity = document.createElement("div");
      identity.className = "flight-identity";
      const no = document.createElement("strong");
      no.textContent = flight.flightNo || "航班";
      const route = document.createElement("small");
      route.textContent = (flight.deptCity || "—") + " → " + (flight.arrCity || "—") + " · " + (flight.flightDate || "");
      identity.append(no, route);
      const time = document.createElement("div");
      time.className = "flight-time";
      const first = document.createElement("strong");
      first.textContent = timePart(flight.firstDeptTime || flight.deptTime);
      const rail = document.createElement("span");
      rail.className = "time-route";
      const last = document.createElement("strong");
      last.textContent = timePart(flight.lastArrTime || flight.arrTime);
      time.append(first, rail, last);
      const price = document.createElement("div");
      price.className = "flight-price";
      const amount = document.createElement("strong");
      amount.textContent = "¥ " + (flight.totalPrice === undefined ? "—" : Number(flight.totalPrice).toFixed(2));
      const seats = document.createElement("small");
      seats.textContent = flight.availableSeats === undefined ? "余票信息未提供" : "实时余票";
      const count = document.createElement("div");
      count.className = "seat-count" + (flight.availableSeats < 0 ? " unavailable" : "");
      count.textContent = flight.availableSeats === undefined ? "" : (flight.availableSeats < 0 ? "维护中" : flight.availableSeats + " 张");
      price.append(amount, seats, count);
      const book = document.createElement("button");
      book.className = "button button-secondary";
      book.textContent = "带入预订";
      book.disabled = flight.availableSeats === -1;
      book.addEventListener("click", () => {
        $("bookingFlightId").value = flight.id || "";
        goTo("orders");
        $("bookingFlightId").focus();
      });
      card.append(identity, time, price, book);
      root.appendChild(card);
    });
  }

  function timePart(value) {
    if (!value) return "--:--";
    const match = String(value).match(/T(\d{2}:\d{2})/);
    return match ? match[1] : String(value).slice(0, 5);
  }

  function renderOrders(result) {
    const root = $("orderList");
    root.replaceChildren();
    const orders = result && Array.isArray(result.data) ? result.data : [];
    if (!orders.length) {
      const empty = document.createElement("div");
      empty.className = "empty-state compact";
      empty.textContent = result && result.msg ? result.msg : "当前没有订单。";
      root.appendChild(empty);
      return;
    }
    orders.forEach((order) => {
      const row = document.createElement("div");
      row.className = "order-row";
      const copy = document.createElement("div");
      copy.className = "order-row-copy";
      const no = document.createElement("strong");
      no.textContent = order.orderNo || String(order.orderId);
      const summary = document.createElement("small");
      summary.textContent = (order.flightNo || "航班") + " · " + (order.flightDate || "") + " · ¥" + (order.totalPrice || "—");
      copy.append(no, summary);
      const status = document.createElement("span");
      status.className = "status-tag " + (order.status || "");
      status.textContent = order.status || "UNKNOWN";
      const detail = document.createElement("button");
      detail.textContent = "详情";
      detail.addEventListener("click", () => {
        $("orderId").value = order.orderId;
        loadOrderDetail(order.orderId);
      });
      row.append(copy, status, detail);
      root.appendChild(row);
    });
  }

  async function loadOrderDetail(orderId) {
    if (!orderId) return;
    const result = await sendRequest({ label: "订单详情", method: "GET", path: "/pri/order/detail", query: { order_id: orderId } });
    if (result.result && result.result.code === 200 && result.result.data) setSelectedOrder(result.result.data);
  }

  function parseJsonField(id, defaultValue) {
    const raw = $(id).value.trim();
    if (!raw) return defaultValue;
    return JSON.parse(raw);
  }

  document.querySelectorAll(".nav-item").forEach((button) => button.addEventListener("click", () => goTo(button.dataset.panel)));
  document.querySelectorAll("[data-goto]").forEach((button) => button.addEventListener("click", () => goTo(button.dataset.goto)));
  $("refreshStatus").addEventListener("click", checkBackend);
  $("saveBase").addEventListener("click", () => {
    storage.setItem("hakimi-api-base", $("apiBase").value.trim());
    toast("API Base URL 已保存");
  });
  $("saveToken").addEventListener("click", () => {
    storage.setItem("hakimi-token", tokenValue());
    updateAuthUI();
    toast("Token 已保存在当前浏览器");
  });
  $("clearToken").addEventListener("click", () => {
    $("tokenInput").value = "";
    storage.removeItem("hakimi-token");
    updateAuthUI();
    toast("已清除登录 Token");
  });
  $("tokenInput").addEventListener("input", updateAuthUI);
  $("clearHistory").addEventListener("click", () => { requestHistory = []; paintHistory(); toast("请求记录已清空"); });

  $("loginForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const body = { email: $("loginEmail").value.trim(), password: $("loginPassword").value };
    const result = await sendRequest({ label: "用户登录", method: "POST", path: "/pri/user/login", body, auth: false, hideBody: true });
    if (result.result && result.result.code === 200 && typeof result.result.data === "string") {
      $("tokenInput").value = result.result.data;
      storage.setItem("hakimi-token", result.result.data);
      updateAuthUI();
      toast("登录成功，Token 已保存");
    }
  });

  $("captchaImageButton").addEventListener("click", async () => {
    const result = await sendRequest({ label: "获取图形验证码", method: "GET", path: "/pri/user/captcha", auth: false });
    if (result.result && result.result.code === 200 && result.result.data) {
      $("captchaKey").value = result.result.data.uuid || "";
      $("captchaImage").src = result.result.data.imgBase64 || "";
      $("captchaImage").hidden = !result.result.data.imgBase64;
      $("captchaPlaceholder").hidden = Boolean(result.result.data.imgBase64);
    }
  });

  $("sendCode").addEventListener("click", async () => {
    const body = {
      email: $("registerEmail").value.trim(),
      picCode: $("captchaCode").value.trim(),
      captchaKey: $("captchaKey").value.trim()
    };
    if (!body.email || !body.picCode || !body.captchaKey) { toast("请先填写邮箱并获取、输入图形验证码", true); return; }
    const result = await sendRequest({ label: "发送邮箱验证码", method: "POST", path: "/pri/user/send_code", body, auth: false });
    if (result.result && result.result.code === 200 && result.result.data) {
      captchaSession = result.result.data.sessionToken || "";
      $("sessionToken").value = captchaSession;
      $("registerHint").textContent = "sessionToken 已获取；打开 Mailpit 本地收件箱查看邮箱验证码。";
    }
  });

  $("registerForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!$("sessionToken").value) { toast("请先完成图形验证码与发送邮箱验证码", true); return; }
    const body = {
      email: $("registerEmail").value.trim(),
      userName: $("registerName").value.trim(),
      password: $("registerPassword").value,
      verifyCode: $("verifyCode").value.trim(),
      sessionToken: $("sessionToken").value.trim()
    };
    const result = await sendRequest({ label: "注册用户", method: "POST", path: "/pri/user/register", body, auth: false, hideBody: true });
    if (result.result && result.result.code === 200) toast("注册成功，可以使用该邮箱登录");
  });

  $("searchForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    $("resultCount").textContent = "查询中…";
    const body = {
      deptCity: $("deptCity").value.trim(),
      arrCity: $("arrCity").value.trim(),
      flightDate: $("flightDate").value,
      sortType: Number($("sortType").value)
    };
    const result = await sendRequest({ label: "高性能航班搜索", method: "POST", path: "/pri/flight/search", body });
    renderFlights(result.result);
  });

  $("dbSearch").addEventListener("click", async () => {
    const body = {
      deptCity: $("deptCity").value.trim(),
      arrCity: $("arrCity").value.trim(),
      flightDate: $("flightDate").value,
      sortType: Number($("sortType").value)
    };
    const result = await sendRequest({ label: "数据库降级搜索", method: "POST", path: "/pri/flight/search_flight", body });
    renderFlights(result.result);
  });

  $("bookingForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const claims = tokenClaims(tokenValue());
    if (!claims || claims.id === undefined) { toast("请先登录，Token 中需要包含用户 ID", true); goTo("account"); return; }
    const flightId = Number($("bookingFlightId").value);
    if (!flightId) { toast("请输入有效的航班 ID", true); return; }
    const body = { flightId, userId: String(claims.id), seatPrefer: $("seatPrefer").value };
    const result = await sendRequest({ label: "航班预订", method: "POST", path: "/pri/flight/booking", body });
    if (result.result && result.result.code === 200 && result.result.data && result.result.data.orderId) {
      $("orderId").value = result.result.data.orderId;
      setSelectedOrder(result.result.data);
      toast("预订成功，订单 ID 已填入订单操作");
    }
  });

  $("loadOrders").addEventListener("click", async () => {
    const status = $("orderStatus").value;
    const result = await sendRequest({ label: "我的订单列表", method: "GET", path: "/pri/order/list", query: status ? { status } : {} });
    renderOrders(result.result);
  });

  $("orderDetailForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    await loadOrderDetail($("orderId").value.trim());
  });
  $("cancelOrder").addEventListener("click", async () => {
    const orderId = $("orderId").value.trim();
    if (!orderId) { toast("请先填写订单 ID", true); return; }
    const result = await sendRequest({ label: "取消未支付订单", method: "POST", path: "/pri/order/cancel", body: { orderId } });
    if (result.result && result.result.code === 200) await loadOrderDetail(orderId);
  });
  $("refundOrder").addEventListener("click", async () => {
    const orderId = $("orderId").value.trim();
    if (!orderId) { toast("请先填写订单 ID", true); return; }
    const result = await sendRequest({ label: "申请退款", method: "POST", path: "/pri/order/refund", body: { orderId } });
    if (result.result && result.result.code === 200) await loadOrderDetail(orderId);
  });
  $("payOrder").addEventListener("click", async () => {
    const orderId = $("orderId").value.trim();
    if (!orderId) { toast("请先填写订单 ID", true); return; }
    await sendRequest({ label: "请求支付 HTML", method: "POST", path: "/pri/order/pay", query: { order_id: orderId }, body: null });
  });

  $("initData").addEventListener("click", async () => {
    const days = Number($("initDays").value);
    if (!window.confirm("将生成 " + days + " 天后的演示航班数据。继续吗？")) return;
    await sendRequest({ label: "初始化演示数据", method: "GET", url: devUrl("/dev/init", { days }) });
  });
  $("syncData").addEventListener("click", async () => {
    await sendRequest({ label: "同步航班数据", method: "GET", url: devUrl("/dev/flight/sync") });
  });
  $("saleState").addEventListener("click", async () => {
    const id = $("saleFlightId").value.trim();
    if (!id) { toast("请输入航班 ID", true); return; }
    await sendRequest({ label: "查询航班销售状态", method: "GET", url: devUrl("/dev/flight/" + encodeURIComponent(id) + "/sale-state") });
  });
  $("saleResume").addEventListener("click", async () => {
    const id = $("saleFlightId").value.trim();
    if (!id) { toast("请输入航班 ID", true); return; }
    if (!window.confirm("尝试校验数据并恢复航班 " + id + " 销售？")) return;
    await sendRequest({ label: "恢复航班销售", method: "POST", url: devUrl("/dev/flight/" + encodeURIComponent(id) + "/sale-resume") });
  });
  $("createFlight").addEventListener("click", async () => {
    const ids = $("newSegmentIds").value.split(",").map((value) => Number(value.trim())).filter((value) => Number.isFinite(value) && value > 0);
    if (!$("newFlightNo").value.trim() || !ids.length) { toast("请填写航班号和至少一个航段实例 ID", true); return; }
    const body = { flightNo: $("newFlightNo").value.trim(), segmentInstanceIds: ids };
    await sendRequest({ label: "创建新航班", method: "POST", url: devUrl("/dev/flight/new"), body });
  });

  $("customRequestForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const method = $("customMethod").value;
    const path = $("customPath").value.trim();
    if (!path.startsWith("/")) { toast("API 路径需要以 / 开头", true); return; }
    try {
      const query = parseJsonField("customQuery", {});
      const body = method === "GET" || method === "HEAD" ? undefined : parseJsonField("customBody", {});
      const url = path.startsWith("/dev/") || path === "/dev"
        ? devUrl(path, query)
        : apiUrl(path.startsWith("/api/v1/") ? path.slice("/api/v1".length) : path, query);
      await sendRequest({ label: "自定义 API 请求", method, url, body, noToken: $("customNoToken").checked });
    } catch (error) { toast("JSON 格式无效：" + error.message, true); }
  });

  $("apiBase").value = safeRead("hakimi-api-base", "/api/v1");
  $("tokenInput").value = safeRead("hakimi-token", "");
  $("flightDate").value = datePlus(1);
  $("customBody").value = JSON.stringify({
    deptCity: "北京", arrCity: "成都", flightDate: datePlus(1), sortType: 1
  }, null, 2);
  updateAuthUI();
  paintHistory();
  checkBackend();
})();
