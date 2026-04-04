package com.example.springbootbase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置属性。
 * 对应 application.yml 中 app.cors 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * 允许的来源地址。
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * 允许的请求方法。
     */
    private List<String> allowedMethods = new ArrayList<>();

    /**
     * 允许携带 Cookie。
     */
    private boolean allowCredentials = true;

    /**
     * 预检请求缓存时间（秒）。
     */
    private long maxAge = 3600;
}
