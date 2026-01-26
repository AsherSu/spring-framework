package org.springframework.spring_reading.aop.Pointcut;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface MyMethodAnnotation {
	String value() default "";
}
