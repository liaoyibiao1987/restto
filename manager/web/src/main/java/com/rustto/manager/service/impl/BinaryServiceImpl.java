package com.rustto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rustto.manager.common.BusinessException;
import com.rustto.manager.common.PageResult;
import com.rustto.manager.common.ResultCode;
import com.rustto.manager.config.BackupProperties;
import com.rustto.manager.entity.ClientBinary;
import com.rustto.manager.mapper.ClientBinaryMapper;
import com.rustto.manager.netty.BinaryDistributor;
import com.rustto.manager.service.BinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * 二进制版本服务实现。
 */
@Service
@RequiredArgsConstructor
public class BinaryServiceImpl extends ServiceImpl<ClientBinaryMapper, ClientBinary>
        implements BinaryService {

    private final BackupProperties backupProperties;

    private final BinaryDistributor binaryDistributor;

    @Override
    public ClientBinary store(MultipartFile file, String version) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件为空");
        }
        Path dir = Paths.get(backupProperties.getDataDir(), "binaries", version);
        Files.createDirectories(dir);
        String original = file.getOriginalFilename() == null ? "rustto-client.bin" : file.getOriginalFilename();
        Path dest = dir.resolve(original);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        String checksum = sha256Hex(dest);
        long size = Files.size(dest);

        ClientBinary binary = new ClientBinary();
        binary.setVersion(version);
        binary.setFilePath(dest.toString());
        binary.setChecksum(checksum);
        binary.setSize(size);
        binary.setUploadedAt(LocalDateTime.now());
        save(binary);
        return binary;
    }

    @Override
    public boolean pushToNode(Long binaryId, Long nodeId) {
        ClientBinary binary = getById(binaryId);
        if (binary == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "二进制不存在");
        }
        return binaryDistributor.push(nodeId, Paths.get(binary.getFilePath()),
                binary.getVersion(), binary.getChecksum());
    }

    @Override
    public PageResult<ClientBinary> page(long page, long size) {
        Page<ClientBinary> result = page(new Page<>(page, size),
                new QueryWrapper<ClientBinary>().orderByDesc("uploaded_at"));
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }

    /**
     * 计算文件 sha256 的十六进制摘要（Java 8 兼容，流式读取）。
     *
     * @param file 文件
     * @return 十六进制 sha256
     * @throws IOException 读取失败
     */
    private static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    digest.update(buf, 0, n);
                }
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("compute sha256 failed", e);
        }
    }

    /**
     * 字节数组转十六进制小写串（Java 8 无 HexFormat）。
     *
     * @param bytes 字节
     * @return 十六进制串
     */
    private static String toHex(byte[] bytes) {
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(hex[(b >> 4) & 0xF]).append(hex[b & 0xF]);
        }
        return sb.toString();
    }
}
