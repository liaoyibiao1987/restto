package com.restto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restto.manager.common.PageResult;
import com.restto.manager.entity.SysOperLog;
import com.restto.manager.mapper.SysOperLogMapper;
import com.restto.manager.service.OperLogService;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现：分页查询 + 清空。
 */
@Service
public class OperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog> implements OperLogService {

    @Override
    public PageResult<SysOperLog> page(String title, String operUser, long page, long size) {
        QueryWrapper<SysOperLog> qw = new QueryWrapper<>();
        if (title != null && !title.isEmpty()) {
            qw.like("title", title);
        }
        if (operUser != null && !operUser.isEmpty()) {
            qw.like("oper_user", operUser);
        }
        qw.orderByDesc("oper_time");
        Page<SysOperLog> p = new Page<>(page, size);
        Page<SysOperLog> result = page(p, qw);
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }

    @Override
    public void clearAll() {
        remove(new QueryWrapper<>());
    }
}
