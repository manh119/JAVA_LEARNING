package com.engineerpro.example.redis.service;

import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.CacheProperties.Redis;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Service
public class DistributedLockImp implements DistributedLock {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean lock(String key, String value, long timeoutMillis) {
        log.info("Locking key: {}, value: {}, timeout: {}", key, value, timeoutMillis);

        long maxWaitMillis = 3000; // tối đa 3 giây chờ để thử lại
        long startTime = System.currentTimeMillis();

        long baseDelay = 100; // delay ban đầu 100ms
        int attempt = 0;

        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofMillis(timeoutMillis));
            if (Boolean.TRUE.equals(success)) {
                log.info("✅ Lock acquired: {}", key);
                return true;
            }

            // exponential backoff with jitter
            attempt++;
            long delay = Math.min(baseDelay * (1L << attempt), 1000); // tăng dần, giới hạn 1s
            long jitter = (long) (Math.random() * delay); // thêm jitter để tránh các thread retry cùng lúc

            log.warn("Lock busy, retrying in {}ms... [attempt {}]", jitter, attempt);
            try {
                Thread.sleep(jitter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.warn("❌ Failed to acquire lock within {}ms: {}", maxWaitMillis, key);
        return false;
    }

    @Override
    public void unlock(String key, String value) {
        log.info("Unlocking key: {}, value: {}", key, value);

        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "   return redis.call('del', KEYS[1]) " +
                "else " +
                "   return 0 " +
                "end";

        Long result = redisTemplate.execute(
                (RedisConnection connection) -> connection.scriptingCommands().eval(
                        luaScript.getBytes(),
                        ReturnType.INTEGER,
                        1,
                        key.getBytes(),
                        value.getBytes()),
                true);

        if (result != null && result == 1) {
            log.info("Unlocked key {} successfully", key);
        } else {
            log.warn("Failed to unlock key {}. It may not be locked by value {}", key, value);
        }
    }

}
