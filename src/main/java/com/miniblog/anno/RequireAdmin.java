package com.miniblog.anno;

import com.miniblog.validation.StateValidation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented//元注解表示该注解可以抽取到文档中
@Target({METHOD, FIELD, ANNOTATION_TYPE,CONSTRUCTOR, PARAMETER, TYPE_USE})//元注解：表示可以用在方法、字段、注解类型、构造方法、参数、类型使用时
@Retention(RUNTIME)//元注解：表示注解能在哪个阶段被保留这里是运行时注解（RUNTIME）
public @interface RequireAdmin {

}
