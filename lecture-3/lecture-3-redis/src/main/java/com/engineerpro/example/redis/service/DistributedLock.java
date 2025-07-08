package com.engineerpro.example.redis.service;

import org.springframework.stereotype.Service;

@Service
public interface DistributedLock {

    boolean lock(String key, String value, long timeout);

    void unlock(String key, String value);
}
