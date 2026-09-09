package com.atguigu.tingshu.common.login;


import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class TSLoginAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    @Around("execution(* com.atguigu.tingshu.*.api.*.*(..)) && @annotation(tsLogin)")
    public Object doBasicProfiling(ProceedingJoinPoint pjp, TSLogin tsLogin) throws Throwable {
        // 前置逻辑
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes sra = (ServletRequestAttributes) requestAttributes;
        HttpServletRequest request = sra.getRequest();
        String token = request.getHeader("token");
        String loginKey = RedisConstant.USER_LOGIN_KEY_PREFIX + token;
        UserInfoVo UserInfoVo =(UserInfoVo) redisTemplate.opsForValue().get(loginKey);
        if(UserInfoVo == null && tsLogin.required()){
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
        }
        if(UserInfoVo != null){
            AuthContextHolder.setUserId(UserInfoVo.getId());
        }
        // 执行目标方法
        Object retval = pjp.proceed();

        // 后置逻辑
        AuthContextHolder.removeUserId();
        return retval;
    }
}
