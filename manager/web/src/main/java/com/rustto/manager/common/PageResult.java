package com.rustto.manager.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果包装。
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页。 */
    private long page;
    /** 每页大小。 */
    private long size;
    /** 总条数。 */
    private long total;
    /** 数据列表。 */
    private List<T> records;

    /**
     * 构造分页结果。
     *
     * @param page    当前页
     * @param size    每页大小
     * @param total   总条数
     * @param records 数据列表
     * @param <T>     元素类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        return new PageResult<>(page, size, total, records);
    }
}
