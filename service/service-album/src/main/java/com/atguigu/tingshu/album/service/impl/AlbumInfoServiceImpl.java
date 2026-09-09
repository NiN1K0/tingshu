package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.atguigu.tingshu.album.mapper.AlbumAttributeValueMapper;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.AlbumAttributeValueService;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumAttributeValueVo;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.atguigu.tingshu.common.constant.SystemConstant.*;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class AlbumInfoServiceImpl extends ServiceImpl<AlbumInfoMapper, AlbumInfo> implements AlbumInfoService {

    @Autowired
    private AlbumInfoMapper albumInfoMapper;

    @Autowired
    private AlbumAttributeValueMapper albumAttributeValueMapper;

    @Autowired
    private AlbumAttributeValueService albumAttributeValueService;

    @Autowired
    private AlbumStatMapper albumStatMapper;

    @Autowired
    private TrackInfoMapper trackInfoMapper;

    @Autowired
    private AuditService auditService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAlbumInfo(AlbumInfoVo albumInfoVo, Long userId) {
        AlbumInfo albumInfo = BeanUtil.copyProperties(albumInfoVo, AlbumInfo.class);
        albumInfo.setUserId(userId);
        String payType = albumInfo.getPayType();
        if (ALBUM_PAY_TYPE_VIPFREE.equals(payType) || ALBUM_PAY_TYPE_REQUIRE.equals(payType)) {
            albumInfo.setTracksForFree(3);
        }
        albumInfo.setStatus(ALBUM_STATUS_NO_PASS);

        albumInfoMapper.insert(albumInfo);
        Long albumId = albumInfo.getId();

        List<AlbumAttributeValueVo> albumAttributeValueVoList = albumInfoVo.getAlbumAttributeValueVoList();
        if (CollUtil.isNotEmpty(albumAttributeValueVoList)) {
            List<AlbumAttributeValue> albumAttributeValueList = albumAttributeValueVoList.stream().map(vo -> {
                AlbumAttributeValue albumAttributeValue = BeanUtil.copyProperties(vo, AlbumAttributeValue.class);
                albumAttributeValue.setAlbumId(albumId);
                return albumAttributeValue;
            }).collect(Collectors.toList());
            albumAttributeValueService.saveBatch(albumAttributeValueList);
        }
        // 初始化专辑统计信息
        this.saveAlbumInfoStat(albumId, ALBUM_STAT_PLAY, 0);
        this.saveAlbumInfoStat(albumId, ALBUM_STAT_SUBSCRIBE, 0);
        this.saveAlbumInfoStat(albumId, ALBUM_STAT_BUY, 0);
        this.saveAlbumInfoStat(albumId, ALBUM_STAT_COMMENT, 0);

        // Todo: 对文字进行审核
        String content = albumInfo.getAlbumTitle() + albumInfo.getAlbumIntro();
        String suggestion = auditService.auditText(content);
        if("block".equals(suggestion)){
            albumInfo.setStatus(ALBUM_STATUS_NO_PASS);
        } else if ("review".equals(suggestion)) {
            albumInfo.setStatus(ALBUM_STATUS_MANUAL);
        }else if("pass".equals(suggestion)){
            albumInfo.setStatus(ALBUM_STATUS_PASS);
        }
        albumInfoMapper.updateById(albumInfo);
    }

    @Override
    public void saveAlbumInfoStat(Long albumId, String statType, int statNum) {
        AlbumStat albumStat = new AlbumStat();
        albumStat.setAlbumId(albumId);
        albumStat.setStatType(statType);
        albumStat.setStatNum(statNum);
        albumStatMapper.insert(albumStat);
    }

    @Override
    public IPage<AlbumListVo> findUserAlbumPage(IPage<AlbumListVo> pageInfo, AlbumInfoQuery albumInfoQuery) {
        return albumInfoMapper.findUserAlbumPage(pageInfo, albumInfoQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAlbumInfo(Long id) {
        Long count = trackInfoMapper.selectCount(new LambdaQueryWrapper<TrackInfo>().eq(TrackInfo::getAlbumId, id));
        if (count > 0) {
            throw new RuntimeException("请先删除专辑中的声音");
        }
        albumInfoMapper.deleteById(id);
//		albumAttributeValueService.remove(new LambdaQueryWrapper<AlbumAttributeValue>().eq(AlbumAttributeValue::getAlbumId, id));
        albumAttributeValueMapper.delete(new LambdaQueryWrapper<AlbumAttributeValue>().eq(AlbumAttributeValue::getAlbumId, id));
        albumStatMapper.delete(new LambdaQueryWrapper<AlbumStat>().eq(AlbumStat::getAlbumId, id));
    }

    @Override
    public AlbumInfo getAlbumInfo(Long id) {
        AlbumInfo albuminfo = this.getById(id);
        List<AlbumAttributeValue> attributeValues = albumAttributeValueService.list(new LambdaQueryWrapper<AlbumAttributeValue>().eq(AlbumAttributeValue::getAlbumId, id));
        if(CollUtil.isNotEmpty(attributeValues)){
            albuminfo.setAlbumAttributeValueVoList(attributeValues);
        }
        return albuminfo;
    }

    @Override
    public void updateAlbumInfo(Long id, AlbumInfoVo albumInfoVo) {
        AlbumInfo albumInfo = BeanUtil.copyProperties(albumInfoVo, AlbumInfo.class);
        albumInfo.setId(id);
        albumInfo.setStatus(ALBUM_STATUS_NO_PASS);
        albumInfoMapper.updateById(albumInfo);
        // 删除旧关联标签信息
        albumAttributeValueMapper.delete(new LambdaQueryWrapper<AlbumAttributeValue>().eq(AlbumAttributeValue::getAlbumId, id));
        // 新增修改后的关联标签信息
        List<AlbumAttributeValueVo> albumAttributeValueVoList = albumInfoVo.getAlbumAttributeValueVoList();
        if (CollUtil.isNotEmpty(albumAttributeValueVoList)) {
            List<AlbumAttributeValue> albumAttributeValueList = albumAttributeValueVoList.stream().map(vo -> {
                AlbumAttributeValue albumAttributeValue = BeanUtil.copyProperties(vo, AlbumAttributeValue.class);
                albumAttributeValue.setAlbumId(id);
                return albumAttributeValue;
            }).collect(Collectors.toList());
            albumAttributeValueService.saveBatch(albumAttributeValueList);
        }
        // Todo: 对文字进行审核
        String content = albumInfo.getAlbumTitle() + albumInfo.getAlbumIntro();
        String suggestion = auditService.auditText(content);
        if("block".equals(suggestion)){
            albumInfo.setStatus(ALBUM_STATUS_NO_PASS);
        } else if ("review".equals(suggestion)) {
            albumInfo.setStatus(ALBUM_STATUS_MANUAL);
        }else if("pass".equals(suggestion)){
            albumInfo.setStatus(ALBUM_STATUS_PASS);
        }
        albumInfoMapper.updateById(albumInfo);
    }

    @Override
    public List<AlbumInfo> findUserAllAlbumList(Long userId) {
        LambdaQueryWrapper<AlbumInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AlbumInfo::getUserId, userId);
        queryWrapper.orderByDesc(AlbumInfo::getCreateTime);
        queryWrapper.select(AlbumInfo::getId, AlbumInfo::getAlbumTitle);
        queryWrapper.last("limit 100");
        return albumInfoMapper.selectList(queryWrapper);
    }
}
