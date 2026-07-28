package com.itheima.big_event.validation;

import com.itheima.big_event.anno.State;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

//自定义校验器
//泛型参数表示校验器校验的注解类型和校验的字段类型
public class StateValidation implements ConstraintValidator<State, String> {
    //提高校验规则
    /*
     * @param value 校验的字段值
     * @param constraintValidatorContext 校验上下文对象
     * @return 校验是否通过
     * */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null) {
            return false;
        }
        if (value.equals("草稿") || value.equals("已发布")) {
            return true;
        }
        return false;
    }
}
