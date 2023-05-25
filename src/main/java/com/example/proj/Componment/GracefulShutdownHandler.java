package com.example.proj.Componment;

import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GracefulShutdownHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GracefulShutdownHandler.class);

    @PreDestroy
    public void onShutdown() {
        LOGGER.info("Received SIGTERM signal. Starting graceful shutdown process.");
    }
}
