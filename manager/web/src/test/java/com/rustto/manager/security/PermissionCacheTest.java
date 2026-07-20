package com.rustto.manager.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PermissionCache} 单测：命中/过期/失效/并发。
 */
class PermissionCacheTest {

    @Test
    void cachesUntilExpiry() throws InterruptedException {
        // TTL 10ms：连续两次立即调用命中，sleep 后过期重载
        PermissionCache cache = new PermissionCache(TimeUnit.MILLISECONDS.toNanos(10));
        AtomicInteger loads = new AtomicInteger();
        Set<String> codes = new HashSet<>();
        codes.add("system:user:list");

        Set<String> first = cache.getOrLoad(1L, () -> {
            loads.incrementAndGet();
            return codes;
        });
        Set<String> cached = cache.getOrLoad(1L, () -> {
            loads.incrementAndGet();
            return new HashSet<>();
        });
        assertSame(first, cached);
        assertEquals(1, loads.get());

        // 等待过期后再取，应重新加载
        Thread.sleep(40);
        cache.getOrLoad(1L, () -> {
            loads.incrementAndGet();
            return codes;
        });
        assertEquals(2, loads.get());
    }

    @Test
    void invalidateRemovesOnlyTargetUser() {
        PermissionCache cache = new PermissionCache(TimeUnit.SECONDS.toNanos(120));
        Set<String> codes = new HashSet<>();
        codes.add("p1");
        cache.getOrLoad(1L, () -> codes);
        cache.getOrLoad(2L, () -> codes);

        cache.invalidate(1L);
        AtomicInteger loads = new AtomicInteger();
        cache.getOrLoad(1L, () -> {
            loads.incrementAndGet();
            return codes;
        });
        assertEquals(1, loads.get(), "user 1 应被失效需重新加载");

        cache.getOrLoad(2L, () -> {
            loads.incrementAndGet();
            return codes;
        });
        assertEquals(1, loads.get(), "user 2 仍命中，不应重新加载");
    }

    @Test
    void invalidateAllClearsEverything() {
        PermissionCache cache = new PermissionCache(TimeUnit.SECONDS.toNanos(120));
        Set<String> codes = new HashSet<>();
        codes.add("p1");
        cache.getOrLoad(1L, () -> codes);
        cache.getOrLoad(2L, () -> codes);
        cache.invalidateAll();

        AtomicInteger loads = new AtomicInteger();
        cache.getOrLoad(1L, () -> {
            loads.incrementAndGet();
            return codes;
        });
        assertEquals(1, loads.get());
    }

    @Test
    void concurrentGetOrLoadDoesNotThrow() throws InterruptedException {
        PermissionCache cache = new PermissionCache(TimeUnit.SECONDS.toNanos(120));
        int threads = 16;
        CountDownLatch latch = new CountDownLatch(threads);
        ConcurrentHashMap<String, String> errors = new ConcurrentHashMap<>();
        for (int i = 0; i < threads; i++) {
            final long uid = i % 4;
            Thread t = new Thread(() -> {
                try {
                    cache.getOrLoad(uid, () -> {
                        Set<String> s = new HashSet<>();
                        s.add("x");
                        return s;
                    });
                } catch (Exception e) {
                    errors.put("err", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            t.start();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "并发不应抛异常");
    }

    @Test
    void loaderResultIsStoredVerbatim() {
        PermissionCache cache = new PermissionCache(TimeUnit.SECONDS.toNanos(120));
        Set<String> codes = new HashSet<>();
        codes.add("a");
        Set<String> got = cache.getOrLoad(7L, () -> codes);
        assertSame(codes, got);
        assertNotSame(codes, new HashSet<>(codes));
    }
}
