package com.atguigu.tingshu.user.service.impl;


import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.WxMaUserService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

	@Autowired
	private UserInfoMapper userInfoMapper;

	@Autowired
	private WxMaService wxMaService;

	@Autowired
	private RedisTemplate redisTemplate;

    @Autowired
    private RabbitService rabbitService;

	@Override
	public Map<String, String> wxLogin(String code) {
        try {
            WxMaUserService userService = wxMaService.getUserService();
            WxMaJscode2SessionResult sessionInfo = userService.getSessionInfo(code);
            Assert.notNull(sessionInfo, "登录,code:{}失败", code);
            String openid = sessionInfo.getOpenid();
            UserInfo userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getWxOpenId, openid));
            if (userInfo == null) {
                userInfo = new UserInfo();
                userInfo.setWxOpenId(openid);
                userInfo.setNickname("听友" + UUID.randomUUID());
                userInfo.setAvatarUrl("https://1482247251.vod-qcloud.com/f914b366vodcq1482247251/10f66efd5001834818961254304/eayAzMDWxDsA.png");
                userInfoMapper.insert(userInfo);
            }

            Map<String, Object> map = new HashMap<>();
            map.put("userId", userInfo.getId());
            map.put("totalAmount",new BigDecimal(1000.00));
            map.put("title","新用户专项体验金活动");
            map.put("orderNo","ZS" + IdUtil.getSnowflakeNextIdStr());
            rabbitService.sendMessage(MqConst.EXCHANGE_USER,MqConst.ROUTING_USER_REGISTER,map);

            String token = UUID.randomUUID().toString();
            String loginKey = RedisConstant.USER_LOGIN_KEY_PREFIX + token;
            UserInfoVo userInfoVo = BeanUtil.copyProperties(userInfo, UserInfoVo.class);
            redisTemplate.opsForValue().set(loginKey, userInfoVo, RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);

            return Map.of("token",token);
        } catch (WxErrorException e) {
			log.error("微信登录失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public UserInfoVo getUserInfo(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        return BeanUtil.copyProperties(userInfo, UserInfoVo.class);
    }

    @Override
    public void updateUser(Long userId, UserInfoVo userInfoVo) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setNickname(userInfoVo.getNickname());
        userInfo.setAvatarUrl(userInfoVo.getAvatarUrl());

        userInfoMapper.updateById(userInfo);
    }
}
