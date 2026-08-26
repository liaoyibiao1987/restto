package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.backup.binary.ClientBinary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户端二进制版本 Mapper。
 */
@Mapper
public interface ClientBinaryMapper extends BaseMapper<ClientBinary> {
}
