package com.github.ajharry69.log.mask;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.jdk.StringSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

public class MaskingSerializer extends StdSerializer<Object> {

    private final MaskingService maskingService;
    private final P11MaskingProperties properties;
    private final P11MaskingProperties.MaskingStyle styleOverride;
    private final String maskCharacterOverride;

    public MaskingSerializer(MaskingService maskingService, P11MaskingProperties properties) {
        this(maskingService, properties, null, null);
    }

    private MaskingSerializer(MaskingService maskingService,
                              P11MaskingProperties properties,
                              P11MaskingProperties.MaskingStyle styleOverride,
                              String maskCharacterOverride) {
        super(Object.class);
        this.maskingService = maskingService;
        this.properties = properties;
        this.styleOverride = styleOverride;
        this.maskCharacterOverride = maskCharacterOverride;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
        gen.writeString(maskingService.mask(value.toString(), styleOverride, maskCharacterOverride));
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property == null) return new StringSerializer();

        var annotation = findMaskAnnotation(property);
        if (annotation != null) {
            return new MaskingSerializer(maskingService, properties, annotation.style(), annotation.maskCharacter());
        }

        if (properties.isFieldConfigured(property.getName())) {
            return this;
        }

        return new StringSerializer();
    }

    private Mask findMaskAnnotation(BeanProperty property) {
        var annotation = property.getAnnotation(Mask.class);
        if (annotation != null) return annotation;
        annotation = property.getContextAnnotation(Mask.class);
        if (annotation != null) return annotation;
        var member = property.getMember();
        return member != null ? member.getAnnotation(Mask.class) : null;
    }
}
