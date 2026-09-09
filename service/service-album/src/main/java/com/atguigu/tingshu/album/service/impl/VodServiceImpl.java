package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.util.UploadFileUtil;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.qcloud.vod.VodUploadClient;
import com.qcloud.vod.model.VodUploadRequest;
import com.qcloud.vod.model.VodUploadResponse;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;


@Slf4j
@Service
public class VodServiceImpl implements VodService {

    @Autowired
    private VodConstantProperties vodConstantProperties;

    @Autowired
    private VodUploadClient vodUploadClient;

    @Autowired
    private VodClient vodClient;

    @Override
    public Map<String, String> uploadTrack(MultipartFile file) {
        try {
            String filePath = UploadFileUtil.uploadTempPath(vodConstantProperties.getTempPath(), file);
            VodUploadRequest request = new VodUploadRequest();
            request.setMediaFilePath(filePath);
            VodUploadResponse response = vodUploadClient.upload("ap-guangzhou", request);
            if(response != null){
                String fileId = response.getFileId();
                String mediaUrl = response.getMediaUrl();
                return Map.of("mediaFileId", fileId, "mediaUrl", mediaUrl);
            }
            return null;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public TrackMediaInfoVo getMediaInfo(String mediaFileId) {
        try {
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DescribeMediaInfosRequest req = new DescribeMediaInfosRequest();
            String[] fileIds1 = {mediaFileId};
            req.setFileIds(fileIds1);

            // 发起请求获取音频文件详情列表
            DescribeMediaInfosResponse resp = vodClient.DescribeMediaInfos(req);
            if(resp != null){
                MediaInfo[] mediaInfoSet = resp.getMediaInfoSet();
                if(mediaInfoSet != null && mediaInfoSet.length > 0){
                    MediaInfo mediaInfo = mediaInfoSet[0];
                    String type = mediaInfo.getBasicInfo().getType();
                    MediaMetaData metaData = mediaInfo.getMetaData();
                    Float audioDuration = metaData.getAudioDuration();
                    Long size = metaData.getSize();
                    TrackMediaInfoVo vo = new TrackMediaInfoVo();
                    vo.setDuration(audioDuration);
                    vo.setSize(size);
                    vo.setType(type);
                    return vo;
                }
            }
        } catch (TencentCloudSDKException e) {
            log.error("获取媒体文件信息失败", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public void deleteMedia(String mediaFileId) {
        try {
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DeleteMediaRequest req = new DeleteMediaRequest();
            req.setFileId(mediaFileId);
            vodClient.DeleteMedia(req);
        } catch (TencentCloudSDKException e) {
            log.error("删除媒体文件失败", e);
        }

    }
}
