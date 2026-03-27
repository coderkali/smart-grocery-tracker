package com.smartgrocery.modules.ai.infrastructure.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to document tool parameters for the AI.
 * (Used in place of Spring AI 1.0.0's proprietary @ToolParam while on M5)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    String description() default "";
}
