package com.atguigu.gmall.scheduled;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.atguigu.gmall.scheduled.mapper")
public class GmallScheduledApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmallScheduledApplication.class, args);
    }

}
