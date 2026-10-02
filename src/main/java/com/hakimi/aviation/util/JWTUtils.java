package com.hakimi.aviation.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import com.hakimi.aviation.entity.User;

import java.util.Date;

/**
 * JWT工具类
 * payload包含信息，可通过Base64解密，故不应包含账号密码等敏感信息
 * 无法作废已颁发的token，除非改密钥
 */
public class JWTUtils {

    /**
     * 设置过期时间 ———— 一周
     */
    private static final long EXPIRE = 60000 * 60 * 24 * 7;
    /**
     * 设置加密密钥
     */
    private static final String SECRET = System.getenv("HAKIMI_JWT_SECRET") != null ?
            System.getenv("HAKIMI_JWT_SECRET") :
            "default_secret_for_dev";

    /**
     * 令牌前缀
     */
    private static final String TOKEN_PREFIX = "hajimi";
    /**
     * subject
     */
    private static final String SUBJECT = "hajimi";

    /**
     * 根据用户信息，生成令牌
     * @param user 实体类
     * @return Token 令牌
     */
    public static String generateJsonWebToken(User user){

        String token = Jwts.builder().setSubject(SUBJECT)
                .claim("head_img", user.getAvatar())
                .claim("id", user.getId())
                .claim("name", user.getUserName())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE) )
                .signWith(SignatureAlgorithm.HS256, SECRET).compact();

        token = TOKEN_PREFIX + token;

        return token;
    }

    public static Claims checkJwt(String token){

        try{
            final Claims claims = Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token.replace(TOKEN_PREFIX, "")).getBody();
            return claims;
        }catch(Exception e){
            return null;
        }

    }


}
