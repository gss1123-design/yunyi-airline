package com.hakimi.aviation.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;

@Component
public class EmailUtil {

    @Autowired
    private JavaMailSender mailSender;

    // 注入 Thymeleaf 的核心引擎
    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async("smsThreadPool")
    public void sendHtmlVerificationCode(String targetEmail, String code) {
        try {
            //创建邮件体
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            // true 表示需要创建一个 multipart message（支持 HTML）
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail,"哈基米航空");
            helper.setTo(targetEmail);
            helper.setSubject("邮箱身份验证码");

            //准备模板需要的数据 (把 Java 里的 code 变量放进大模型里)
            Context context = new Context();
            context.setVariable("code", code); // 这里的 "code" 必须和 HTML 里的 ${code} 对应！

            //把数据注入到 email-code.html 模板里，生成最终的 HTML 字符串
            String htmlContent = templateEngine.process("email-code", context);

            // 4. 把渲染好的 HTML 塞进邮件体，第二个参数 true 代表这是一封 HTML 邮件
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);

            System.out.println("====== 邮件异步发送成功至: " + targetEmail + " ======");

        } catch (Exception e) {
            System.err.println("邮件发送失败：" + e.getMessage());
        }
    }
}
