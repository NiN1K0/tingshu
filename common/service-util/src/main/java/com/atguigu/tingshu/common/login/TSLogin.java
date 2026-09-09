package com.atguigu.tingshu.common.login;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface TSLogin {
    boolean required() default true;
    Class<? extends Throwable>[] rollbackFor() default {};
}
