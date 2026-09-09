package com.atguigu.tingshu.search.service;

public interface SearchService {

    /**
     * 保存专辑索引库文档到 ES
     *
     * @param albumId 专辑 id
     */
    void saveAlbumInfoIndex(Long albumId);

    /**
     * 删除专辑索引库文档（下架）
     *
     * @param albumId 专辑 id
     */
    void removeAlbumInfoIndex(Long albumId);

}
