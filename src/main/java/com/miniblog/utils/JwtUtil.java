package com.miniblog.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private static String KEY;

    @Value("${jwt.secret}")
    public void setKey(String key) {
        KEY = key;
    }

    /**
     * 生成 JWT Token
     * @param claims 业务数据（比如用户id、用户名）
     * @return 生成的 Token 字符串
     */
    public static String genToken(Map<String, Object> claims) {
        return JWT.create()
                .withClaim("claims", claims)
                .withExpiresAt(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 12))
                .sign(Algorithm.HMAC256(KEY));
    }

    /**
     * 解析并验证 Token
     * @param token 前端传来的 Token 字符串
     * @return 解析后的业务数据
     */
    public static Map<String, Object> parseToken(String token) {
        JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(KEY)).build();
        DecodedJWT decodedJWT = jwtVerifier.verify(token);
        return decodedJWT.getClaim("claims").asMap();
    }
}
