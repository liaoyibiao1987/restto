package com.restto.manager.security;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 用户权限码的进程内短 TTL 缓存。
 *
 * <p>用 {@link ConcurrentHashMap} + 纳秒级过期戳实现，避免引入 Caffeine 等新依赖。
 * 缓存命中省去权限 join 查询；权限变更时通过 {@link #invalidate(Long)} / {@link #invalidateAll()} 主动失效，
 * 兜底由 TTL（{@link #TTL_SECONDS}）保证最终权限一致。
 *
 * <p>注意：本缓存为单实例语义；若部署多实例需替换为 Redis 实现（接口语义已预留）。
 */
@Component
public class PermissionCache {

    /** 缓存存活秒数。 */
    static final long TTL_SECONDS = 120;

    private static final class Entry {
        final Set<String> codes;
        final long expireAtNanos;

        Entry(Set<String> codes, long ttlNanos) {
            this.codes = codes;
            this.expireAtNanos = System.nanoTime() + ttlNanos;
        }

        boolean isExpired(long nowNanos) {
            return expireAtNanos - nowNanos <= 0;
        }
    }

    private final ConcurrentHashMap<Long, Entry> store = new ConcurrentHashMap<>();

    private final long ttlNanos;

    /** 默认 TTL 构造（生产）。 */
    public PermissionCache() {
        this(TimeUnit.SECONDS.toNanos(TTL_SECONDS));
    }

    /** 可注入 TTL 的构造（便于测试）。 */
    PermissionCache(long ttlNanos) {
        this.ttlNanos = ttlNanos;
    }

    /**
     * 取用户权限码：命中且未过期则返回缓存，否则加载并回填。
     *
     * @param userId 用户 ID
     * @param loader 未命中时的加载逻辑
     * @return 权限码集合
     */
    public Set<String> getOrLoad(Long userId, Supplier<Set<String>> loader) {
        long now = System.nanoTime();
        Entry entry = store.get(userId);
        if (entry != null && !entry.isExpired(now)) {
            return entry.codes;
        }
        Set<String> fresh = loader.get();
        store.put(userId, new Entry(fresh, ttlNanos));
        return fresh;
    }

    /**
     * 失效单个用户的权限缓存（用于该用户的角色分配/删除）。
     *
     * @param userId 用户 ID
     */
    public void invalidate(Long userId) {
        store.remove(userId);
    }

    /** 失效全部权限缓存（用于角色/权限维度的变更）。 */
    public void invalidateAll() {
        store.clear();
    }
}
