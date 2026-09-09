package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.album.config.MinioConstantProperties;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.album.service.FileUploadService;
import com.atguigu.tingshu.common.execption.GuiguException;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    @Autowired
    private MinioConstantProperties minioConstantProperties;
    @Autowired
    private AuditService auditService;

    @Override
    public String fileUpload(MultipartFile multipartFile) {
        try {
            BufferedImage bufferedImage = ImageIO.read(multipartFile.getInputStream());
            if (bufferedImage == null) {
                throw new GuiguException(400, "文件格式错误");
            }
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            if(width > 900 || height > 900){
                throw new GuiguException(400, "文件大小超出限制");
            }
            // Todo:对图片进行审核
            String suggestion = auditService.auditImage(multipartFile);
            if(StringUtils.isNotBlank(suggestion)){
                if("block".equals(suggestion) || "review".equals(suggestion))
                    throw new GuiguException(400, "文件存在违规");
            }

            // 将文件上传到MInIO
            // 生成带有文件夹目录文件名称 形式：/日期/随机文件名称.后缀
            String folder = DateUtil.today();
            String fileName = IdUtil.randomUUID();
            String extName = FileUtil.extName(multipartFile.getOriginalFilename());
            String objName = "/" + folder + "/" + fileName + "." + extName;
            minioConstantProperties.minioClient().putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConstantProperties.getBucketName())
                            .object(objName)
                            .stream(multipartFile.getInputStream(), multipartFile.getSize(), -1)
                            .contentType(multipartFile.getContentType())
                            .build()
            );
            return minioConstantProperties.getEndpointUrl() + "/" + minioConstantProperties.getBucketName() + objName;
        } catch (GuiguException e) {
            log.error("文件上传失败", e);
            throw new GuiguException(e.getCode(), e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
