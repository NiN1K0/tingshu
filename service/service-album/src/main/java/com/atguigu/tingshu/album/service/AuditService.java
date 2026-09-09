package com.atguigu.tingshu.album.service;

import org.springframework.web.multipart.MultipartFile;

public interface AuditService {
    String auditText(String content);

    String auditImage(MultipartFile multipartFile);

    String startReviewTask(String MediaFileId);

    String getReviewTaskResult(String taskId);
}
