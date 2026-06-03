package com.filer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.filer.mapper")
@EnableAsync
@EnableScheduling
public class FilerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FilerApplication.class, args);
    }
}
