package com.atguigu.tingshu.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.AttributeValueIndex;
import com.atguigu.tingshu.search.repository.AlbumInfoIndexRepository;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;


@Slf4j
@Service
@SuppressWarnings({"all"})
public class SearchServiceImpl implements SearchService {

    // 热度计算权重（和为 1.0）
    private static final double PLAY_WEIGHT = 0.2;
    private static final double SUBSCRIBE_WEIGHT = 0.3;
    private static final double BUY_WEIGHT = 0.4;
    private static final double COMMENT_WEIGHT = 0.1;

    @Autowired
    private AlbumFeignClient albumFeignClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private AlbumInfoIndexRepository albumInfoIndexRepository;

    @Override
    public void saveAlbumInfoIndex(Long albumId) {
        // 1. 远程获取专辑信息
        Result<AlbumInfo> result = albumFeignClient.getAlbumInfo(albumId);
        if (result == null || result.getData() == null) {
            return;
        }
        AlbumInfo albumInfo = result.getData();

        // 2. 远程获取三级分类视图
        Result<BaseCategoryView> categoryResult = albumFeignClient.getCategoryView(albumInfo.getCategory3Id());
        BaseCategoryView categoryView = categoryResult == null ? null : categoryResult.getData();

        // 3. 远程获取主播昵称
        UserInfoVo userInfoVo = userFeignClient.getUserInfoVo(albumInfo.getUserId());

        // 4. 封装 AlbumInfoIndex（copyProperties 覆盖同名字段：id/albumTitle/albumIntro/coverUrl/includeTrackCount/isFinished/payType/createTime/category3Id）
        AlbumInfoIndex index = BeanUtil.copyProperties(albumInfo, AlbumInfoIndex.class);
        index.setAnnouncerName(userInfoVo == null ? null : userInfoVo.getNickname());
        if (categoryView != null) {
            index.setCategory1Id(categoryView.getCategory1Id());
            index.setCategory2Id(categoryView.getCategory2Id());
            index.setCategory3Id(categoryView.getCategory3Id());
        }
        // 专辑属性值
        if (CollUtil.isNotEmpty(albumInfo.getAlbumAttributeValueVoList())) {
            List<AttributeValueIndex> attributeValueIndexList = albumInfo.getAlbumAttributeValueVoList().stream()
                    .map(attr -> {
                        AttributeValueIndex avi = new AttributeValueIndex();
                        avi.setAttributeId(attr.getAttributeId());
                        avi.setValueId(attr.getValueId());
                        return avi;
                    }).collect(Collectors.toList());
            index.setAttributeValueIndexList(attributeValueIndexList);
        }

        // 5. 生成随机统计值
        int playStatNum = ThreadLocalRandom.current().nextInt(1000, 100000);
        int subscribeStatNum = ThreadLocalRandom.current().nextInt(100, 10000);
        int buyStatNum = ThreadLocalRandom.current().nextInt(10, 1000);
        int commentStatNum = ThreadLocalRandom.current().nextInt(50, 5000);
        index.setPlayStatNum(playStatNum);
        index.setSubscribeStatNum(subscribeStatNum);
        index.setBuyStatNum(buyStatNum);
        index.setCommentStatNum(commentStatNum);

        // 6. 加权计算热度（保留两位小数）
        double hotScore = playStatNum * PLAY_WEIGHT + subscribeStatNum * SUBSCRIBE_WEIGHT
                + buyStatNum * BUY_WEIGHT + commentStatNum * COMMENT_WEIGHT;
        index.setHotScore(Math.round(hotScore * 100) / 100.0);

        // 7. 保存到 ES（albuminfo 索引）
        albumInfoIndexRepository.save(index);
    }

    @Override
    public void removeAlbumInfoIndex(Long albumId) {
        // 下架：删除 ES 索引文档
        albumInfoIndexRepository.deleteById(albumId);
    }
}
