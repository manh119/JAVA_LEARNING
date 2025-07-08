package com.engineerpro.example.redis.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistributedLockImpTest {

    @Autowired
    private DistributedLockImp distributedLockImp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void testOnlyOneThreadCanAcquireLock() throws InterruptedException {
        String key = "lock:multi";
        long timeout = 30000L;
        int threadCount = 10000;

        redisTemplate.delete(key); // ensure key clean before test

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<String> ownerValue = new AtomicReference<>();

        Runnable task = () -> {
            String value = UUID.randomUUID().toString();
            try {
                startLatch.await();
                boolean locked = distributedLockImp.lock(key, value, timeout);
                if (locked && successCount.incrementAndGet() == 1) {
                    ownerValue.set(value);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        };

        for (int i = 0; i < threadCount; i++) {
            new Thread(task).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertEquals(1, successCount.get(), "Only one thread should acquire the lock");

        String value = ownerValue.get();
        if (value != null) {
            distributedLockImp.unlock(key, value);
        }
    }
}
