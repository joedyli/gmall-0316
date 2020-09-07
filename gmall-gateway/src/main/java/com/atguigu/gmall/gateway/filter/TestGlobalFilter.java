package com.atguigu.gmall.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TestGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        System.out.println("全局过滤器已经产生作用，当前请求被拦截。。。。。。。。");
        // 放行
        return chain.filter(exchange);
    }

    /**
     * 返回值越小优先级越高
     * @return
     */
    @Override
    public int getOrder() {
        return 10;
    }
}
