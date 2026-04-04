package com.example.springbootbase;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动类。
 * 仅用于启动 Spring Boot 应用，并扫描 Mapper 包。
 */
@SpringBootApplication
@MapperScan("com.example.springbootbase.mapper")
public class SpringbootBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootBaseApplication.class, args);
    }
}
