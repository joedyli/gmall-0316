package com.atguigu.gmall.cart.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Component
@Slf4j
public class CartUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {
    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... objects) {
        log.error("子任务出现异常，方法：{}，参数：{}，异常信息：{}", method.getName(), Arrays.asList(objects), throwable.getMessage());
    }
}
