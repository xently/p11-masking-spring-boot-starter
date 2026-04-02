package com.github.ajharry69.log.mask;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface Mask {
    P11MaskingProperties.MaskingStyle style() default P11MaskingProperties.MaskingStyle.DEFAULT;

    String maskCharacter() default "";
}
