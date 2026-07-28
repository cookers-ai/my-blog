package com.itheima.big_event.anno;

import com.itheima.big_event.validation.StateValidation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/*
* 自定义注解state*/
@Documented//元注解表示该注解可以抽取到文档中
@Target({METHOD,FIELD})//元注解：表示可以用在方法、
@Retention(RUNTIME)//元注解：表示注解能在哪个阶段被保留这里是运行时注解（RUNTIME）
@Constraint(validatedBy = StateValidation.class)//表示指定该注解的校验器是StateValidation类（提供校验的规则）
public @interface State {
    //提高校验失败的提示信息
    String message() default "state信息只能说草稿或者已发布";
  //指定分组
    Class<?>[] groups() default{};
//负载 可以获取到State注解的其他信息
    Class<? extends Payload>[] payload() default {};
}
