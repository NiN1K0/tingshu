package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.UserAccountDetailMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

	@Autowired
	private UserAccountMapper userAccountMapper;

	@Autowired
	private UserAccountDetailMapper userAccountDetailMapper;

	@Override
	public void initUserAccount(Map<String, Object> map) {
		Long userId =(Long) map.get("userId");
		String title =(String) map.get("title");
		BigDecimal totalAmount =(BigDecimal) map.get("totalAmount");
		String orderNo =(String) map.get("orderNo");
		UserAccount userAccount = new UserAccount();
		userAccount.setUserId(userId);
		userAccount.setTotalAmount(totalAmount);
		userAccount.setLockAmount(BigDecimal.ZERO);
		userAccount.setAvailableAmount(totalAmount);
		userAccount.setTotalIncomeAmount(totalAmount);
		userAccount.setTotalPayAmount(BigDecimal.ZERO);
		int insert = userAccountMapper.insert(userAccount);
		if (insert > 0){
			UserAccountDetail userAccountDetail = new UserAccountDetail();
			userAccountDetail.setUserId(userId);
			userAccountDetail.setTitle(title);
			userAccountDetail.setTradeType(SystemConstant.ACCOUNT_TRADE_TYPE_DEPOSIT);
			userAccountDetail.setAmount(totalAmount);
			userAccountDetail.setOrderNo(orderNo);
			userAccountDetailMapper.insert(userAccountDetail);
		}
	}
}
