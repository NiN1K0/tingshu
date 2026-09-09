package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.codec.Base64;
import com.atguigu.tingshu.album.service.AuditService;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ims.v20201229.ImsClient;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationRequest;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.tms.v20201229.TmsClient;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    @Autowired
    private TmsClient tmsClient;

    @Autowired
    private ImsClient imsClient;

    @Autowired
    private VodClient vodClient;

    @Override
    public String auditText(String content) {
        try {
            TextModerationRequest req = new TextModerationRequest();
            req.setContent(Base64.encode(content));
            // 返回的resp是一个TextModerationResponse的实例，与请求对象对应
            TextModerationResponse resp = tmsClient.TextModeration(req);
            if (resp != null){
                String suggestion = resp.getSuggestion();
                log.info("文本:{}，处置意见：{}", content, suggestion);
                return suggestion.toLowerCase();
            }
        } catch (TencentCloudSDKException e) {
            log.error("文本内容审核异常：{}", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public String auditImage(MultipartFile multipartFile) {
        try {
            // 实例化一个请求对象,每个接口都会对应一个request对象
            ImageModerationRequest req = new ImageModerationRequest();
            req.setFileContent(Base64.encode(multipartFile.getInputStream()));
            // 返回的resp是一个ImageModerationResponse的实例，与请求对象对应
            ImageModerationResponse resp = imsClient.ImageModeration(req);
            if(resp != null){
                String suggestion = resp.getSuggestion();
                log.info("图片:{}，处置意见：{}", multipartFile.getOriginalFilename(), suggestion);
                return suggestion.toLowerCase();
            }
        } catch (IOException e) {
            log.error("图片内容审核异常：{}", e);
            throw new RuntimeException(e);
        } catch (TencentCloudSDKException e) {
            log.error("图片内容审核异常：{}", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public String startReviewTask(String MediaFileId) {
        try {
            // 实例化一个请求对象,每个接口都会对应一个request对象
            ReviewAudioVideoRequest req = new ReviewAudioVideoRequest();
            req.setFileId(MediaFileId);
            // 返回的resp是一个ReviewAudioVideoResponse的实例，与请求对象对应
            ReviewAudioVideoResponse resp = vodClient.ReviewAudioVideo(req);
            if(resp != null){
                return resp.getTaskId();
            }
        } catch (TencentCloudSDKException e) {
            log.error("启动审核任务失败:{}", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public String getReviewTaskResult(String taskId) {
        try {
            DescribeTaskDetailRequest req = new DescribeTaskDetailRequest();
            req.setTaskId(taskId);
            // 返回的resp是一个DescribeTaskDetailResponse的实例，与请求对象对应
            DescribeTaskDetailResponse resp = vodClient.DescribeTaskDetail(req);
            if(resp != null){
                if("ReviewAudioVideo".equals(resp.getTaskType())) {
                    if("FINISH".equals(resp.getStatus())){
                        ReviewAudioVideoTask reviewAudioVideoTask = resp.getReviewAudioVideoTask();
                        if("FINISH".equals(reviewAudioVideoTask.getStatus())){
                            ReviewAudioVideoTaskOutput output = reviewAudioVideoTask.getOutput();
                            String suggestion = output.getSuggestion();
                            return suggestion;
                        }
                    }
                }
            }
        } catch (TencentCloudSDKException e) {
            log.error("获取审核任务结果失败:{}", e);
            throw new RuntimeException(e);
        }
        return null;
    }
}
