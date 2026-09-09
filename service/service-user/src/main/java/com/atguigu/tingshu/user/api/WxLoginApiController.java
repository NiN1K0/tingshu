package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.TSLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "微信授权登录接口")
@RestController
@RequestMapping("/api/user/wxLogin")
@Slf4j
public class WxLoginApiController {

    @Autowired
    private UserInfoService userInfoService;

    @Operation(summary = "微信授权登录")
    @GetMapping("/wxLogin/{code}")
    public Result<Map<String,String>> wxLogin(@PathVariable String code) {
        Map<String,String> map = userInfoService.wxLogin(code);
        return Result.ok(map);
    }

    @Operation(summary = "获取登录用户信息")
    @GetMapping("/getUserInfo")
    @TSLogin
    public Result<UserInfoVo> getUserInfo() {
        Long userId = AuthContextHolder.getUserId();
        UserInfoVo userInfoVo = userInfoService.getUserInfo(userId);
        return Result.ok(userInfoVo);
    }

    @Operation(summary = "更新用户信息")
    @PostMapping("/updateUser")
    @TSLogin
    public Result updateUser(@RequestBody UserInfoVo userInfoVo) {
        Long userId = AuthContextHolder.getUserId();
        userInfoService.updateUser(userId, userInfoVo);
        return Result.ok();
    }
}
