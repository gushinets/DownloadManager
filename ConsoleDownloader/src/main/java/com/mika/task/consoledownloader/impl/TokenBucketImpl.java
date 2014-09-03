package com.mika.task.consoledownloader.impl;

import com.mika.task.consoledownloader.TokenBucket;
import org.springframework.util.Assert;

/**
 * Simple implementation of TokenBucket algorithm for traffic shaping.
 * Refer to http://en.wikipedia.org/wiki/Token_bucket
 *
 * @author Mikhail Gushinets
 * @since 01/09/2014
 */
class TokenBucketImpl implements TokenBucket {
    /**
     * Download speed limit.
     */
    private long speedLimit;

    /**
     * How many more tokens are available in the bucket at the moment.
     */
    private long currentTokensCount;

    /**
     * Flag that helps to stop this thread safely.
     */
    private volatile boolean keepAlive;

    /**
     * Time to sleep before refreshing bandwidth to desired value.
     */
    private static final int TIME_TO_SLEEP = 1000;

    /**
     * Constructor.
     * @param bytesPerSecond download speed limit.
     */
    TokenBucketImpl(long bytesPerSecond) {
        Assert.isTrue(bytesPerSecond > 0, "Speed Limit must be positive value");

        speedLimit = bytesPerSecond;
        keepAlive = true;
    }

    /**
     * Stops this threads` work.
     */
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
