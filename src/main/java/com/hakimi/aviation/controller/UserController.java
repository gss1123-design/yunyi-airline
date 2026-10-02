package com.hakimi.aviation.controller;

import com.hakimi.aviation.common.JsonData;
import com.hakimi.aviation.entity.User;
import com.hakimi.aviation.model.request.user.LoginRequest;
import com.hakimi.aviation.model.request.user.RegisterRequest;
import com.hakimi.aviation.model.request.user.SendCodeRequest;
import com.hakimi.aviation.service.user.UserService;
import com.wf.captcha.SpecCaptcha;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("api/v1/pri/user")
public class UserController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    @PostMapping("register")
    public JsonData<User> register(@RequestBody RegisterRequest request){

        //调用service层接口进行注册 在此之前需要通过图形验证码 申请发送邮箱验证码
        //service层还需要检验邮箱验证码是否正确，并对邮箱加锁，避免多端同时注册
        int success = userService.register(request);

        return success == 1 ? JsonData.buildSuccess("注册成功") : JsonData.buildError("注册失败");
    }

    @PostMapping("send_code")
    public JsonData<Map<String,String>> sendVerifyCode(@RequestBody SendCodeRequest request){

        //验证邮箱和图形验证码是否正确，返回会话令牌供注册时校验邮箱一致性
        String sessionToken = userService.sendVerifyCode(request);

        Map<String, String> data = new HashMap<>();
        data.put("sessionToken", sessionToken);

        return JsonData.buildSuccess(data, "验证码已发送至您的邮箱");
    }

    /**
     * 此 mapping 用来生成图形验证码 并将验证码值存入到Redis（UUID作为Key）
     * @return JsonData 包含 UUID 和 Base64 图片
     */
    @GetMapping("captcha")
    public JsonData<Map<String,String>> getCaptcha() {
        //配置图形验证码的基本参数 (宽130, 高48, 4位字符)
        SpecCaptcha specCaptcha = new SpecCaptcha(130, 48, 4);

        //获取生成的验证码文本 (转小写，方便后续忽略大小写校验)
        String code = specCaptcha.text().toLowerCase();

        //生成全局唯一的 UUID 作为 Redis 的 Key
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String redisKey = "captcha:" + uuid;

        //将验证码文本存入 Redis，设置 120 秒过期时间
        stringRedisTemplate.opsForValue().set(redisKey, code, 120, TimeUnit.SECONDS);

        //将 UUID 和 Base64 图片组装返回给前端
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("uuid", uuid);
        resultMap.put("imgBase64", specCaptcha.toBase64());

        return JsonData.buildSuccess(resultMap,"图形验证码信息已发送至前端");
    }

    /**
     * 登录接口 登录请求是高频操作，需建立 email 和 password 的联合索引
     * @param request DTO
     * @return 登录成功发放 Token
     */
    @PostMapping("login")
    public JsonData<String> login(@RequestBody LoginRequest request){

        String token = userService.login(request);

        return token != null
                ? JsonData.buildSuccess(token,"登录成功 已发放令牌 为期一周")
                : JsonData.buildError("登录失败");
    }

}