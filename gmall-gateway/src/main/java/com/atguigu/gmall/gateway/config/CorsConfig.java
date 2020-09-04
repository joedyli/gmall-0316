package com.atguigu.gmall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(){

        CorsConfiguration configuration = new CorsConfiguration();
        // *代表所有域名，但是不能携带cookie
        configuration.addAllowedOrigin("http://manager.gmall.com");
        configuration.addAllowedOrigin("http://gmall.com");
        configuration.addAllowedOrigin("http://www.gmall.com");
        //configuration.addAllowedOrigin("http://localhost:1000");
        // 是否允许携带cookie
        configuration.setAllowCredentials(true);
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource configurationSource = new UrlBasedCorsConfigurationSource();
        configurationSource.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(configurationSource);
    }
}
