package co.ke.xently.log.mask;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

public class LogbackMaskingContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var properties = Binder.get(context.getEnvironment())
                .bind("log", LogProperties.class)
                .orElseGet(LogProperties::new);

        var maskingService = new LogMaskingService(properties);
        var forgingService = new LogForgingService(properties);

        if (ClassUtils.isPresent("ch.qos.logback.classic.LoggerContext", context.getClassLoader())) {
            new LogbackMaskingAndForgingInitializer(maskingService, forgingService, properties)
                    .initialize();
        }

        context.getBeanFactory().registerSingleton("logProperties", properties);
        context.getBeanFactory().registerSingleton("logMaskingService", maskingService);
        context.getBeanFactory().registerSingleton("logForgingService", forgingService);
    }
}