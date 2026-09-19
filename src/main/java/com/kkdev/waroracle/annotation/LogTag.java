package com.kkdev.waroracle.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to mark controller methods with an operation-specific identifier tag.
 * Used for dynamic trace and log tagging.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogTag
{
    String value() default "";
}
