package com.rustto.manager.controller;

import com.rustto.manager.common.PageResult;
import com.rustto.manager.common.Result;
import com.rustto.manager.dto.BinaryPushRequest;
import com.rustto.manager.entity.ClientBinary;
import com.rustto.manager.security.OperLog;
import com.rustto.manager.security.RequirePermission;
import com.rustto.manager.service.BinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;

/**
 * 客户端二进制版本接口：上传、查询、下发。
 */
@RestController
@RequestMapping("/api/binaries")
@RequiredArgsConstructor
@RequirePermission("backup:binary:list")
public class BinaryController {

    private final BinaryService binaryService;

    /**
     * 上传新版本二进制。
     *
     * @param file    二进制文件
     * @param version 版本号
     * @return 记录
     * @throws IOException 读写失败
     */
    @PostMapping("/upload")
    @RequirePermission("backup:binary:upload")
    @OperLog("上传二进制")
    public Result<ClientBinary> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam("version") String version) throws IOException {
        return Result.success(binaryService.store(file, version));
    }

    /**
     * 分页查询二进制版本。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<ClientBinary>> page(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        return Result.success(binaryService.page(page, size));
    }

    /**
     * 将指定版本二进制下发到节点。
     *
     * @param id      二进制记录 ID
     * @param request 下发请求
     * @return 是否成功下发
     */
    @PostMapping("/{id}/push")
    @RequirePermission("backup:binary:push")
    @OperLog("下发二进制")
    public Result<Boolean> push(@PathVariable Long id, @RequestBody @Valid BinaryPushRequest request) {
        return Result.success(binaryService.pushToNode(id, request.getNodeId()));
    }
}
