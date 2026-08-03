package com.miniblog;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
class MiniBlogApplicationTests {

    @Test
    void contextLoads() {
        Map<String,Object> map = new HashMap<>();
        map.put("id",1);
        map.put("username","admin");
        //生成jwt
        String token = JWT.create()
                //添加载荷
                .withClaim("user",map).withExpiresAt(new Date(System.currentTimeMillis()+1000*60*5)) //添加过期时间
                .sign(Algorithm.HMAC256("123456"));//指定算法配置密钥
        System.out.println(token);

    }
    @Test
    public void testVerify(){
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjp7ImlkIjoxLCJ1c2VybmFtZSI6ImFkbWluIn0sImV4cCI6MTc3NzM3OTk0MX0.uknMVP4NNoi8DS_t6SAm2iuhSdYyb0FGCelgqkLoPBk\n";
        JWTVerifier jwt = JWT.require(Algorithm.HMAC256("123456")).build();
       DecodedJWT result = jwt.verify(token);
       Map<String, Claim> claims = result.getClaims();
       System.out.println(claims);
    }
    @Test
    public void Demotext() throws FileNotFoundException {
        String accessKey = System.getenv("OSS_ACCESS_KEY");
        String secretKey = System.getenv("OSS_SECRET_KEY");
        String bucket = "mini-blog"; // 👉 区分大小写！和控制台完全一致
        // ===========================================================

        System.out.println("=== 核心参数验证 ===");
        System.out.println("AK长度: " + accessKey.length() + " (正确应为40位)");
        System.out.println("SK长度: " + secretKey.length() + " (正确应为40位)");
        System.out.println("Bucket: " + bucket);

        // 1. 自动识别地区，彻底排除地区错误
        Configuration cfg = new Configuration(Region.autoRegion());
        UploadManager uploadManager = new UploadManager(cfg);

        // 2. 生成凭证
        Auth auth = Auth.create(accessKey.trim(), secretKey.trim()); // 自动去空格
        String upToken = auth.uploadToken(bucket);
        System.out.println("\n✅ 凭证生成成功，长度: " + upToken.length());

        // 3. 验证文件
        File file = new File("D:\\work\\cs\\file\\1.png");
        System.out.println("\n文件验证:");
        System.out.println("路径: " + file.getAbsolutePath());
        System.out.println("存在: " + file.exists());
        System.out.println("大小: " + file.length() + "字节");

        if (!file.exists()) {
            System.err.println("❌ 文件不存在！");
            return;
        }

        // 4. 上传（指定明确文件名）
        try (FileInputStream fis = new FileInputStream(file)) {
            Response response = uploadManager.put(fis, "test-avatar-20260509.png", upToken, null, null);

            System.out.println("\n=== 上传结果 ===");
            System.out.println("状态码: " + response.statusCode);
            System.out.println("响应体: " + response.bodyString());

            if (response.isOK()) {
                DefaultPutRet ret = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
                System.out.println("\n🎉 上传成功！");
                System.out.println("文件名: " + ret.key);
                System.out.println("去七牛云控制台搜索: test-avatar-20260509.png");
            }

        } catch (Exception e) {
            System.err.println("\n 完整错误信息:");
            e.printStackTrace();
        }

    }
}
