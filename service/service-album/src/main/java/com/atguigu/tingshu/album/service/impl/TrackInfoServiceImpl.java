package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qcloud.vod.common.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.atguigu.tingshu.common.constant.SystemConstant.*;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class TrackInfoServiceImpl extends ServiceImpl<TrackInfoMapper, TrackInfo> implements TrackInfoService {

    @Autowired
    private TrackInfoMapper trackInfoMapper;

    @Autowired
    private AlbumInfoMapper albumInfoMapper;

    @Autowired
    private VodService vodService;

    @Autowired
    private TrackStatMapper trackStatMapper;

    @Autowired
    private AuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTrackInfo(TrackInfoVo trackInfoVo, Long userId) {
        // 获取专辑信息
        Long albumId = trackInfoVo.getAlbumId();
        AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
        // 获取专辑包含声音数量
        Integer includeTrackCount = albumInfo.getIncludeTrackCount();
        TrackInfo trackInfo = BeanUtil.copyProperties(trackInfoVo, TrackInfo.class);
        // 设置声音属性
        trackInfo.setUserId(userId);
        trackInfo.setOrderNum(includeTrackCount + 1);
        if (StringUtils.isBlank(trackInfo.getCoverUrl())) {
            trackInfo.setCoverUrl(albumInfo.getCoverUrl());
        }
        trackInfo.setSource(SystemConstant.TRACK_SOURCE_USER);
        trackInfo.setStatus(SystemConstant.TRACK_STATUS_NO_PASS);
        // 通过腾讯云点播平台获取媒体文件信息
        TrackMediaInfoVo trackMediaInfoVo = vodService.getMediaInfo(trackInfo.getMediaFileId());
        if (trackMediaInfoVo != null) {
            trackInfo.setMediaDuration(new BigDecimal(trackMediaInfoVo.getDuration()));
            trackInfo.setMediaSize(trackMediaInfoVo.getSize());
            trackInfo.setMediaType(trackMediaInfoVo.getType());
        }
        // 新增声音
        trackInfoMapper.insert(trackInfo);
        Long trackId = trackInfo.getId();
        // 更新专辑包含声音数量
        albumInfo.setIncludeTrackCount(includeTrackCount + 1);
        albumInfoMapper.updateById(albumInfo);

        // 保存声音统计信息
        this.saveTrackStat(trackId, SystemConstant.TRACK_STAT_PLAY, 0);
        this.saveTrackStat(trackId, SystemConstant.TRACK_STAT_COLLECT, 0);
        this.saveTrackStat(trackId, SystemConstant.TRACK_STAT_PRAISE, 0);
        this.saveTrackStat(trackId, SystemConstant.TRACK_STAT_COMMENT, 0);

        // Todo: 对文字和音频进行审核
        String content = trackInfo.getTrackTitle() + trackInfo.getTrackIntro();
        String suggestion = auditService.auditText(content);
        if("block".equals(suggestion)){
            trackInfo.setStatus(TRACK_STATUS_NO_PASS);
        } else if ("review".equals(suggestion)) {
            trackInfo.setStatus(TRACK_STATUS_MANUAL);
            // Todo: 对音频进行审核
            String taskId = auditService.startReviewTask(trackInfo.getMediaFileId());
            trackInfo.setStatus(TRACK_STATUS_REVIEWING);
            trackInfo.setReviewTaskId(taskId);
        }else if("pass".equals(suggestion)){
            trackInfo.setStatus(TRACK_STATUS_PASS);
            // Todo: 对音频进行审核
            String taskId = auditService.startReviewTask(trackInfo.getMediaFileId());
            trackInfo.setStatus(TRACK_STATUS_REVIEWING);
            trackInfo.setReviewTaskId(taskId);
        }
        trackInfoMapper.updateById(trackInfo);
    }

    @Override
    public void saveTrackStat(Long trackId, String statType, int statNum) {
        TrackStat trackStat = new TrackStat();
        trackStat.setTrackId(trackId);
        trackStat.setStatType(statType);
        trackStat.setStatNum(statNum);
        trackStatMapper.insert(trackStat);
    }

    @Override
    public IPage<TrackListVo> findUserTrackPage(IPage<TrackListVo> pageInfo, TrackInfoQuery trackInfoQuery) {
        return trackInfoMapper.findUserTrackPage(pageInfo, trackInfoQuery);
    }

    @Override
    public void updateTrackInfo(Long id, TrackInfoVo trackInfoVo) {
        TrackInfo trackInfo = trackInfoMapper.selectById(id);
        String oldmediaFileId = trackInfo.getMediaFileId();
        BeanUtil.copyProperties(trackInfoVo, trackInfo);
        // Todo: 对文字进行审核
        String content = trackInfo.getTrackTitle() + trackInfo.getTrackIntro();
        String suggestion = auditService.auditText(content);
        if("block".equals(suggestion)){
            trackInfo.setStatus(TRACK_STATUS_NO_PASS);
        } else if ("review".equals(suggestion)) {
            trackInfo.setStatus(TRACK_STATUS_MANUAL);
        }else if("pass".equals(suggestion)){
            trackInfo.setStatus(TRACK_STATUS_PASS);
        }
        // 判断音频是否修改
        if (!oldmediaFileId.equals(trackInfoVo.getMediaFileId())) {
            TrackMediaInfoVo mediaInfo = vodService.getMediaInfo(trackInfoVo.getMediaFileId());
            if (mediaInfo != null) {
                trackInfo.setMediaFileId(trackInfoVo.getMediaFileId());
                trackInfo.setMediaUrl(trackInfoVo.getMediaUrl());
                trackInfo.setMediaType(mediaInfo.getType());
                trackInfo.setMediaDuration(new BigDecimal(mediaInfo.getDuration()));
                trackInfo.setMediaSize(mediaInfo.getSize());
            }
            // 删除原来的音频文件
            vodService.deleteMedia(oldmediaFileId);
            // Todo:对新音频进行审核
            String taskId = auditService.startReviewTask(trackInfo.getMediaFileId());
            trackInfo.setStatus(TRACK_STATUS_REVIEWING);
            trackInfo.setReviewTaskId(taskId);
        }
        // 更新声音信息
        trackInfoMapper.updateById(trackInfo);
    }

    @Override
    public void removeTrackInfo(Long id) {
        // 根据id查出声音信息（包括专辑id、排序值、媒体文件id）
        TrackInfo trackInfo = trackInfoMapper.selectById(id);
        Long albumId = trackInfo.getAlbumId();
        Integer orderNum = trackInfo.getOrderNum();
        String mediaFileId = trackInfo.getMediaFileId();

        // 删除声音表记录
        trackInfoMapper.deleteById(id);

        // 更新专辑中包含的声音数量
        albumInfoMapper.update(
                null,
                new LambdaUpdateWrapper<AlbumInfo>()
                        .eq(AlbumInfo::getId, albumId)
                        .setSql("include_track_count = include_track_count - 1")
        );

        // 更新专辑中声音的排序值，确保排序值连续
        trackInfoMapper.update(
                null,
                new LambdaUpdateWrapper<TrackInfo>()
                        .eq(TrackInfo::getAlbumId, albumId)
                        .gt(TrackInfo::getOrderNum, orderNum)
                        .setSql("order_num = order_num - 1")
        );

        // 删除腾讯云点播平台中的媒体文件
        vodService.deleteMedia(mediaFileId);

        // 删除声音统计信息
        trackStatMapper.delete(new LambdaUpdateWrapper<TrackStat>()
                .eq(TrackStat::getTrackId, id)
        );

    }
}
