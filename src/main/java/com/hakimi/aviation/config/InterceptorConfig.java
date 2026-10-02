package com.hakimi.aviation.config;

import com.hakimi.aviation.interceptor.LoginInterceptor;
import com.hakimi.aviation.interceptor.CorsInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 *拦截器配置
 *
 * 需要登录权限的url：/api/v1/pri/ 除了注册与登录
 *
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Bean
    LoginInterceptor loginInterceptor(){
        return new LoginInterceptor();
    }

    @Bean
    CorsInterceptor corsInterceptor(){
        return new CorsInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        /**
         * 配置跨域的拦截器，需要放在最上面
         */
        registry.addInterceptor(corsInterceptor()).addPathPatterns("/**");


        //拦截所有需要登录权限的路径，最前面的“/”一定要加
        registry.addInterceptor(loginInterceptor()).addPathPatterns("/api/v1/pri/**").
                //不拦截的路径
               excludePathPatterns("/api/v1/pri/user/login","/api/v1/pri/user/register","/api/v1/pri/user/captcha",
                        "/api/v1/pri/user/send_code","/api/v1/pri/order/pay/callback");


        WebMvcConfigurer.super.addInterceptors(registry);

    }
}
