package com.miniblog.anno;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

@Documented
@Target({METHOD,FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
    //模块
    String module() default "";
    //操作
    String operation();
}
