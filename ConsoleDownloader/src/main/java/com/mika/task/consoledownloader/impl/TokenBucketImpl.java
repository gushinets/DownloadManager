package com.mika.task.consoledownloader.impl;

import com.mika.task.consoledownloader.TokenBucket;
import org.springframework.util.Assert;

// Simple implementation of TokenBucket algorithm for traffic shaping
// Refer to http://en.wikipedia.org/wiki/Token_bucket

class TokenBucketImpl implements TokenBucket {

    private long speedLimit;
    private long currentTokensCount;
    private volatile boolean keepAlive;

    private static final int TIME_TO_SLEEP = 1000;

    TokenBucketImpl(long bytesPerSecond) {
        Assert.isTrue(bytesPerSecond > 0, "Speed Limit must be positive value");

        speedLimit = bytesPerSecond;
        keepAlive = true;
    }

    public void shutdown() {
        keepAlive = false;
    }

    @Override
    public void run() {
        // every 1 second refresh bandwidth to desired value
        while (keepAlive) {
            currentTokensCount = speedLimit;

            try {
                Thread.currentThread().sleep(TIME_TO_SLEEP);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean getTokens(long n) {
        Assert.isTrue(n >= 0, "Tokens amount must be not negative");

        if (n <= currentTokensCount) {
            currentTokensCount -= n;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public long getTokensLeft() {
        return currentTokensCount;
    }
}
