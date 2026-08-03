package com.miniblog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.miniblog.mapper")
@EnableScheduling
@SpringBootApplication
public class MiniBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniBlogApplication.class, args);
    }

}
