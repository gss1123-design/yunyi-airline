package com.hakimi.aviation.interceptor;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import com.hakimi.aviation.annotations.LoginOptional;
import com.hakimi.aviation.util.JWTUtils;
import com.hakimi.aviation.common.JsonData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;


public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 进入Controller以前的方法
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//
//
//
//        try {
//
//            String accessToken = request.getHeader("token");
//            if (accessToken == null) {
//                accessToken = request.getParameter("token");
//            }
//
//            if (StringUtils.isNotBlank(accessToken)) {
//                Claims claims = JWTUtils.checkJwt(accessToken);
//                if (claims == null) {
//                    //告诉登录过期，重新登录
//                    sendJsonMessage(response, JsonData.buildError("登录过期，重新登录"));
//                    return false;
//                }
//
//                Integer id = (Integer) claims.get("id");
//                String name = (String) claims.get("name");
//
//                request.setAttribute("user_id", id);
//                request.setAttribute("name", name);
//
//                return true;
//
//            }
//
//        }catch (Exception e){}
//
//        sendJsonMessage(response, JsonData.buildError("登录过期，重新登录"));
//
//        return false;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1. 极其重要：判断请求拦截到的是不是 Controller 里的方法
        // （如果拦截到的是静态资源等，直接放行）
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        // 2. 核心探测：看看当前这个方法头上，有没有贴咱们的 @LoginOptional 标签！
        boolean isOptional = handlerMethod.hasMethodAnnotation(LoginOptional.class);

        // 3. 去口袋里摸 Token
        String accessToken = request.getHeader("token");
        if (StringUtils.isBlank(accessToken)) {
            accessToken = request.getParameter("token");
        }

        //System.out.println("已拦截此路径，已获取到token："+accessToken);

        // 4. 终极鉴权矩阵开始
        if (StringUtils.isNotBlank(accessToken)) {
            // 【情况 A】：不管这个接口是不是柔性的，只要你带了 Token 来，保安就必须查验真伪！
            try {
                Claims claims = JWTUtils.checkJwt(accessToken);
                if (claims == null) {
                    // Token 伪造或过期
                    sendJsonMessage(response, JsonData.buildError("登录过期，请重新登录"));
                    return false;
                }
                // 💡 查验通过！把 userId 郑重地塞进 request 里
                // 注意：id 在 JWT 里是 JSON 数字，回程被反序列化成 Integer（小值），
                // 不能直接 (Long) 强转，必须走 Number 数值转换，否则 ClassCastException。
                Long id = ((Number) claims.get("id")).longValue();
                String name = (String) claims.get("name");
                String headImg = (String) claims.get("head_img");
                request.setAttribute("user_id", id);
                request.setAttribute("name", name);
                request.setAttribute("head_img",headImg);

                return true; // 尊贵的登录用户，请进！

            } catch (Exception e) {
                e.printStackTrace();
                sendJsonMessage(response, JsonData.buildError("登录状态异常，请重新登录"));
                return false;
            }
        } else {
            // 【情况 B & C】：口袋空空，没带 Token
            if (isOptional) {
                // 【情况 B】：虽然没带 Token，但这个接口贴了 @LoginOptional！
                // 💡 纯游客，不报错，直接放行！（注意：此时 request 里是干干净净的，没有 user_id）
                return true;
            } else {
                // 【情况 C】：没带 Token，接口也没贴标签（比如点赞接口、发评论接口）
                // 🚨 游客想硬闯私密重地，直接乱棍打出！
                sendJsonMessage(response, JsonData.buildError("无权限，请先登录"));
                return false;
            }
        }
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    /**
     * 响应json数据给前端
     * @param response
     * @param obj
     */
    public static void sendJsonMessage(HttpServletResponse response, Object obj){

        try{
            ObjectMapper objectMapper = new ObjectMapper();
            response.setContentType("application/json; charset=utf-8");
            PrintWriter writer = response.getWriter();
            writer.print(objectMapper.writeValueAsString(obj));
            writer.close();
            response.flushBuffer();
        }catch (Exception e){
            e.printStackTrace();
        }


    }
}
