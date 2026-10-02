package com.hakimi.aviation.service.user;

import com.hakimi.aviation.entity.User;
import com.hakimi.aviation.enums.BizCodeEnum;
import com.hakimi.aviation.exception.BizException;
import com.hakimi.aviation.mapper.UserMapper;
import com.hakimi.aviation.model.request.user.LoginRequest;
import com.hakimi.aviation.model.request.user.RegisterRequest;
import com.hakimi.aviation.model.request.user.SendCodeRequest;
import com.hakimi.aviation.service.admin.async.UserDataAsyncService;
import com.hakimi.aviation.util.EmailUtil;
import com.hakimi.aviation.util.JWTUtils;
import com.hakimi.aviation.util.MD5Util;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class UserServiceImpl implements UserService{

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private EmailUtil emailUtil;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserDataAsyncService  userDataAsyncService;

    @Override
    public String sendVerifyCode(SendCodeRequest request) {

        String captchaKey = "captcha:" + request.getCaptchaKey();

        //验证图形验证码是否正确
        boolean picCodeCorrect = this.verifyPicCode(request);
        if(!picCodeCorrect){
            throw new BizException(BizCodeEnum.PIC_CODE_ERROR);
        }
        //图形验证码验证通过 先删除缓存中的验证码 防止多次请求
        stringRedisTemplate.delete(captchaKey);

        //图形验证码正确 发送邮件验证码
        String coolDownKey = "verify:cooldown:"+request.getEmail();
        if(stringRedisTemplate.hasKey(coolDownKey)){
            //防止频繁发送短信 若冷却期未结束则直接抛出异常
            throw new BizException(BizCodeEnum.FREQUENT_SEND_VERIFY);
        }

        //生成六位数随机验证码
        String verifyCode = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));

        //使用Lua脚本，保证写入验证码和重置冷却锁的操作为原子性操作 避免并发情况下的问题
        //设定脚本模板
        String script =
                "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) \n" +
                        "redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4]) \n" +
                        "return 1";

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);


        String smsCodeKey = "verify:code:"+request.getEmail();
        //准备填充脚本的 keys 数据
        List<String> keys = Arrays.asList(smsCodeKey,coolDownKey);

        Object[] args = new Object[]{
                verifyCode,  //验证码本体
                "300",    //验证码的过期时间 单位：秒
                "Locked", //冷却锁的 value
                //TODO 应该换成更合适的时间
                "60"      //冷却锁的过期时间，单位：秒
        };

        //执行脚本
        Long result = stringRedisTemplate.execute(redisScript, keys, args);

        //生成会话令牌，绑定本次验证码的接收邮箱，注册时做显式比对
        String sessionToken = java.util.UUID.randomUUID().toString().replace("-", "");
        String sessionKey = "verify:session:" + sessionToken;
        stringRedisTemplate.opsForValue().set(sessionKey, request.getEmail(), 300, java.util.concurrent.TimeUnit.SECONDS);

        //用工具类 异步发送邮件 网络I/O不阻塞主线程
        emailUtil.sendHtmlVerificationCode(request.getEmail(),verifyCode);

        return sessionToken;
    }

    private boolean verifyPicCode(@NonNull SendCodeRequest request){

        String captchaKey = "captcha:" + request.getCaptchaKey();
        String picCode = request.getPicCode();
        //输入验证码为空 抛出异常
        if(picCode == null || picCode.isEmpty()){
            throw new BizException(BizCodeEnum.EMPTY_PIC_CODE);
        }

        String realPicCode = stringRedisTemplate.opsForValue().get(captchaKey);

        return picCode.equalsIgnoreCase(realPicCode);
    }

    /**
     * 注册账号接口
     * @param request DTO
     * @return 整型数值 代表SQL落库后返回的值 非1则失败
     */
    @Override
    public int register(@NotNull RegisterRequest request) {

        //校验 session 令牌：确保注册邮箱与接收验证码的邮箱一致
        String sessionKey = "verify:session:" + request.getSessionToken();
        String boundEmail = stringRedisTemplate.opsForValue().get(sessionKey);
        if (boundEmail == null) {
            throw new BizException(BizCodeEnum.VERIFY_SESSION_EXPIRED);
        }
        if (!boundEmail.equals(request.getEmail())) {
            throw new BizException(BizCodeEnum.EMAIL_MISMATCH);
        }
        //校验通过后删除 session token，一次性使用
        stringRedisTemplate.delete(sessionKey);

        //先验证邮箱验证码是否正确
        this.verifyEmailCode(request.getEmail(),request.getVerifyCode());

        //向Redis中申请一把注册锁 同一时间只能有一个人持有同一个邮箱进行注册
        String registerLockKey = "register:lock:"+request.getEmail();
        //如果成功 应该返回True
        Boolean isLocked = stringRedisTemplate.opsForValue().setIfAbsent(registerLockKey, "Locked");

        if(Boolean.FALSE.equals(isLocked)){
            //未持有锁，直接抛出异常
            throw new BizException(BizCodeEnum.OP_FREQUENT);
        }

        //没有抛出异常，可以正常开始注册 这里将为用户自动填充完整信息 如果缺少了必要信息 将会返回null
        User user = parseToUser(request);

        //缺少完整信息 注册失败 返回-1
        stringRedisTemplate.delete(registerLockKey);

        //email字段是一个唯一索引 如果使重复使用号码注册 MySQL会自动抛出一个异常
        //只需要在异常处理器里拦截DuplicateKeyException，即可规范化输出
        //TODO 数据库保护
        return userMapper.insert(user);
    }

    private void verifyEmailCode(String email, String code){

        String smsCodeKey = "verify:code:" + email;

        //使用Lua脚本 保证Redis检验验证码的查删操作的原子性
        String script =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "   return redis.call('del', KEYS[1]) " +
                        "else " +
                        "   return 0 " +
                        "end";

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        //得到查询结果
        Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(smsCodeKey), code);
        if(result == 0L){
            //邮箱不一致的情况已在上层 register() 里通过 session token 显式校验
            throw new BizException(BizCodeEnum.VERIFY_CODE_ERROR);
        }
        //若没有抛出异常则说明验证通过
    }

    private User parseToUser(RegisterRequest request){
        User user = new User();
        String userName = request.getUserName();
        String email = request.getEmail();
        //MD5加密
        String MD5Password = MD5Util.calculateMD5(request.getPassword());
        if(userName == null || email == null || request.getPassword() == null){
            throw new BizException(BizCodeEnum.MISSING_INFO);
        }
        String defaultAvatar = "https://tse2-mm.cn.bing.net/th/id/OIP-C.42FZbkBEBlAf4o4b6J_qvAHaHa?o=7rm=3&rs=1&pid=ImgDetMain&o=7&rm=3";

        user.setUserName(userName);
        user.setEmail(email);
        user.setPassword(MD5Password);
        user.setAvatar(defaultAvatar);
        user.setIsDeleted(1);

        return user;
    }

    /**
     * service 层的登录方法
     * @param request DTO
     * @return 发放一个 Token
     */
    @Override
    public String login(LoginRequest request) {

        String email = request.getEmail();
        //将密码原文转为加密后的密文
        String MD5Password = MD5Util.calculateMD5(request.getPassword());
        if(email == null || request.getPassword() == null){
            //返回空，即未发放Token
            return null;
        }

        //TODO 数据库保护
        User user = userMapper.findUserByEmailAndPwd(email, MD5Password);
        if(user == null){
            throw new BizException(BizCodeEnum.LOGIN_FAILED);
        }

        userDataAsyncService.preheatUserOrder(user.getId());

        return JWTUtils.generateJsonWebToken(user);
    }
}
