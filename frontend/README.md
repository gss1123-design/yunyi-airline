# 云翼航旅本地功能测试台

这是面向本地后端联调的轻量静态界面，无需 Node.js 或单独构建。Docker Compose 使用 Nginx 提供页面，并将 API 与开发接口请求转发给后端容器。

启动已部署的本地服务：

    $compose = 'E:\DockerData\hakimi-airline-local\compose.yaml'
    $envFile = 'E:\DockerData\hakimi-airline-local\.env'
    docker compose --env-file $envFile -f $compose up -d frontend

浏览器访问 http://127.0.0.1:5173。邮件验证码收件箱在 http://127.0.0.1:18025。

界面包括登录和注册、航班高性能/数据库搜索、预订、订单查询/详情/支付/取消/退款、开发数据初始化/同步、航班停售状态管理，以及自定义 API 请求。Token 会保存在当前浏览器的 localStorage。支付返回的 HTML 只会显示在响应面板，不会被执行。

如需停止整个本地服务：

    docker compose --env-file $envFile -f $compose down
