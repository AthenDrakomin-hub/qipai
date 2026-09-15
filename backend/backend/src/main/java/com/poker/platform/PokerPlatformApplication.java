package com.poker.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 棋牌竞技平台启动类
 */
@SpringBootApplication
@MapperScan("com.poker.platform.mapper")
@EnableScheduling
public class PokerPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PokerPlatformApplication.class, args);
        System.out.println("================================");
        System.out.println("  棋牌竞技平台后端启动成功!");
        System.out.println("  API 地址: http://localhost:8080/api");
        System.out.println("  H2 Console: http://localhost:8080/api/h2-console");
        System.out.println("================================");
    }
}
