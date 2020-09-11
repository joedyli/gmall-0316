package com.atguigu.gmall.scheduled.spring;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

//@Component
public class SpringTaskDemo {

    /**
     * 每隔5s执行一次
     */
//    @Scheduled(fixedDelay = 5000)
//    public void test(){
//        System.out.println("这是springTask提供的定时任务1：" + System.currentTimeMillis());
//    }

//    @Scheduled(fixedRate = 5000)
//    public void test1(){
//        System.out.println("这是springTask提供的定时任务1：" + System.currentTimeMillis());
//    }

    @Scheduled(cron = "0/10 * * * * *")
    public void test1(){
        System.out.println("这是springTask提供的定时任务1：" + System.currentTimeMillis());
    }
}
