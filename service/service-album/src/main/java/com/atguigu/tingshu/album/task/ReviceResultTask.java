package com.atguigu.tingshu.album.task;

import cn.hutool.core.collection.CollUtil;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ReviceResultTask {

    @Autowired
    private TrackInfoMapper trackInfoMapper;

    @Autowired
    private AuditService auditService;

    @Scheduled(cron = "0/5 * * * * ?")
    public void reviceResult() {
        log.info("开始获取审核结果");
        List<TrackInfo> trackInfoList = trackInfoMapper.selectList(
                new LambdaQueryWrapper<TrackInfo>()
                        .eq(TrackInfo::getStatus, SystemConstant.TRACK_STATUS_REVIEWING)
                        .select(TrackInfo::getId, TrackInfo::getReviewTaskId)
        );
        if(CollUtil.isNotEmpty(trackInfoList)){
            for (TrackInfo trackInfo : trackInfoList) {
                String suggestion = auditService.getReviewTaskResult(trackInfo.getReviewTaskId());
                if(StringUtils.isNotBlank(suggestion)){
                    if("block".equals(suggestion)){
                        trackInfo.setStatus(SystemConstant.TRACK_STATUS_NO_PASS);
                    }else if("review".equals(suggestion)){
                        trackInfo.setStatus(SystemConstant.TRACK_STATUS_MANUAL);
                    }else if("pass".equals(suggestion)){
                        trackInfo.setStatus(SystemConstant.TRACK_STATUS_PASS);
                    }
                    trackInfoMapper.updateById(trackInfo);
                }
            }
        }
    }
}
