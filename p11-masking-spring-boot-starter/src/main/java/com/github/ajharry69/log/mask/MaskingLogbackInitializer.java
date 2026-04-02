package com.github.ajharry69.log.mask;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;

public class MaskingLogbackInitializer {
    private final MaskingService maskingService;
    private final P11MaskingProperties properties;

    public MaskingLogbackInitializer(MaskingService maskingService, P11MaskingProperties properties) {
        this.maskingService = maskingService;
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        MaskingMessageConverter.initialize(maskingService, properties);
        var loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext) {
            registerConverter("m");
            registerConverter("msg");
            registerConverter("message");
            restartPatternEncoders((LoggerContext) loggerFactory);
        }
    }

    private void registerConverter(String key) {
        PatternLayout.defaultConverterMap.put(key, MaskingMessageConverter.class.getName());
        try {
            PatternLayout.DEFAULT_CONVERTER_MAP.put(key, MaskingMessageConverter.class.getName());
        } catch (UnsupportedOperationException ignored) {
            // Some logback versions return unmodifiable maps.
        }
        try {
            PatternLayout.DEFAULT_CONVERTER_SUPPLIER_MAP.put(key, MaskingMessageConverter::new);
        } catch (UnsupportedOperationException ignored) {
            // Some logback versions return unmodifiable maps.
        }
    }

    private void restartPatternEncoders(LoggerContext context) {
        var seen = new java.util.IdentityHashMap<Appender<?>, Boolean>();
        for (Logger logger : context.getLoggerList()) {
            var iterator = logger.iteratorForAppenders();
            while (iterator.hasNext()) {
                var appender = iterator.next();
                if (seen.put(appender, Boolean.TRUE) != null) continue;
                if (appender instanceof OutputStreamAppender<?> streamAppender) {
                    var encoder = streamAppender.getEncoder();
                    if (encoder instanceof PatternLayoutEncoder patternEncoder) {
                        var layout = patternEncoder.getLayout();
                        if (layout instanceof PatternLayout patternLayout) {
                            var instanceMap = patternLayout.getInstanceConverterMap();
                            instanceMap.put("m", MaskingMessageConverter::new);
                            instanceMap.put("msg", MaskingMessageConverter::new);
                            instanceMap.put("message", MaskingMessageConverter::new);
                        }
                        patternEncoder.stop();
                        patternEncoder.start();
                    }
                }
            }
        }
    }
}
