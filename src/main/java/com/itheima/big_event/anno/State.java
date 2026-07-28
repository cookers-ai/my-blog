package com.itheima.big_event.anno;

import com.itheima.big_event.validation.StateValidation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Constraint(validatedBy = StateValidation.class)
public @interface State {
    String message() default "state信息只能说草稿或者已发布";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
