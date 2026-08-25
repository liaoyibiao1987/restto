package com.restto.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restto.manager.common.PageResult;
import com.restto.manager.entity.ClientBinary;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 客户端二进制版本服务。
 */
public interface BinaryService extends IService<ClientBinary> {

    /**
     * 存储上传的二进制并记录版本。
     *
     * @param file    上传文件
     * @param version 版本号
     * @return 记录
     * @throws IOException 读写失败
     */
    ClientBinary store(MultipartFile file, String version) throws IOException;

    /**
     * 将指定版本二进制下发到节点。
     *
     * @param binaryId 二进制记录 ID
     * @param nodeId   节点 ID
     * @return 是否成功下发
     */
    boolean pushToNode(Long binaryId, Long nodeId);

    /**
     * 分页查询。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<ClientBinary> page(long page, long size);
}
