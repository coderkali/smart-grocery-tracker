package com.smartgrocery.modules.ai.infrastructure.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to document tool methods for the AI.
 * (Used in place of Spring AI 1.0.0's proprietary @Tool while on M5)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    String description() default "";
}
