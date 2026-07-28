package com.itheima.big_event.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssConfig {
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String region;
    private String domain;
}
