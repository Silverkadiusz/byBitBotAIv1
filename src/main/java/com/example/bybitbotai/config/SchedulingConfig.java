package com.example.bybitbotai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.bybitbotai.service.TradingService;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableScheduling
@Slf4j
public class SchedulingConfig {

    private final TradingService tradingService;
    private final ApiConfig.TradingConfig tradingConfig;

    @Autowired
    public SchedulingConfig(TradingService tradingService, ApiConfig.TradingConfig tradingConfig) {
        this.tradingService = tradingService;
        this.tradingConfig = tradingConfig;
    }

   // @Scheduled(fixedDelayString = "${trading.interval.minutes:15}000")

   // @Scheduled(fixedDelayString = "${trading.interval.minutes:1}000")
   @Scheduled(fixedRateString = "${trading.interval.milliseconds:150000}")
   public void scheduleTradingCycle() {
        log.info("Uruchamianie zaplanowanego cyklu handlowego, interwał: {} sec",
                tradingConfig.getIntervalSec());
        tradingService.executeTradingCycle();
    }
}