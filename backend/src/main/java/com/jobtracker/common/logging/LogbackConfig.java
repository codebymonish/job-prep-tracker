package com.jobtracker.common.logging;

import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogbackConfig {

    @PostConstruct
    public void init() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        SafeLogRedactor redactor = new SafeLogRedactor();
        redactor.start();
        context.addTurboFilter(redactor);
    }
}
