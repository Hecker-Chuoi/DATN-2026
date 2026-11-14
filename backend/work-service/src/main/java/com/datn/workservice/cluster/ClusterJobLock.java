package com.datn.workservice.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Khoá cho job theo lịch khi chạy nhiều instance ({@code app.cluster.redis-enabled=true}): chỉ một instance chạy mỗi lần.
 * <p>
 * {@code SET key owner NX PX atMost}: ai đặt được thì chạy. Xong việc vẫn giữ khoá tới đủ {@code atLeast} (chặn instance
 * có đồng hồ lệch vài giây chạy lại ngay sau), rồi chỉ chủ khoá mới được nhả. Instance chết giữa chừng thì khoá tự hết
 * hạn sau {@code atMost}. Tắt chế độ cụm (mặc định) thì chạy thẳng, không cần Redis.
 * <p>
 * Redis lỗi lúc lấy khoá thì vẫn chạy: các job đều chạy lại được an toàn (dedupKey / xoá theo điều kiện),
 * bỏ lỡ nhắc hạn trong ngày tệ hơn chạy trùng.
 */
@Slf4j
@Component
public class ClusterJobLock {

    static final String KEY_PREFIX = "datn:job-lock:";

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end
            local hold = tonumber(ARGV[2])
            if hold > 0 then return redis.call('pexpire', KEYS[1], hold) end
            return redis.call('del', KEYS[1])
            """, Long.class);

    private final ObjectProvider<StringRedisTemplate> redis;
    private final boolean enabled;
    private final String owner;

    @Autowired
    public ClusterJobLock(ObjectProvider<StringRedisTemplate> redis,
                          @Value("${app.cluster.redis-enabled:false}") boolean enabled) {
        this(redis, enabled, defaultOwner());
    }

    ClusterJobLock(ObjectProvider<StringRedisTemplate> redis, boolean enabled, String owner) {
        this.redis = redis;
        this.enabled = enabled;
        this.owner = owner;
    }

    /** @return true nếu job đã chạy trên instance này. */
    public boolean runExclusively(String name, Duration atMost, Duration atLeast, Runnable job) {
        if (!enabled) {
            job.run();
            return true;
        }
        String key = KEY_PREFIX + name;
        StringRedisTemplate template = redis.getIfAvailable();
        Boolean acquired;
        try {
            acquired = template == null ? null : template.opsForValue().setIfAbsent(key, owner, atMost);
        } catch (Exception e) {
            log.warn("Không lấy được khoá Redis cho job {} ({}), vẫn chạy trên instance này", name, e.getMessage());
            job.run();
            return true;
        }
        if (acquired == null) {
            log.warn("Chế độ cụm bật nhưng không có Redis, job {} vẫn chạy trên instance này", name);
            job.run();
            return true;
        }
        if (!acquired) {
            log.info("Bỏ qua job {}: instance khác đang hoặc vừa chạy", name);
            return false;
        }
        log.info("Job {} chạy trên instance {}", name, owner);
        long start = System.nanoTime();
        try {
            job.run();
        } finally {
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            long holdMs = Math.max(0, atLeast.toMillis() - elapsedMs);
            try {
                template.execute(RELEASE, List.of(key), owner, Long.toString(holdMs));
            } catch (Exception e) {
                log.warn("Không nhả được khoá {} ({}), khoá tự hết hạn sau {}", key, e.getMessage(), atMost);
            }
        }
        return true;
    }

    private static String defaultOwner() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        return host + ":" + ProcessHandle.current().pid() + ":" + UUID.randomUUID().toString().substring(0, 8);
    }
}
