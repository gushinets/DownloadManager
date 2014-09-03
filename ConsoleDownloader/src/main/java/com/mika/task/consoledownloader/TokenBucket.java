package com.mika.task.consoledownloader;

/**
 * TokenBucket interface for traffic shaping.
 *
 * @author Mikhail Gushinets
 * @since 01/09/2014
 */
public interface TokenBucket extends Runnable {
    /**
     * Tries to remove specified amount of tokens from bucket.
     * If currently available amount of tokens is less than
     * amount specified then request is declined.
     *
     * @param n amount of tokens to remove from bucket.
     * @return true if requested amount of tokens was removed, false otherwise.
     */
    boolean getTokens(long n);

    /**
     * @return returns tokens currently left in the bucket.
     */
    long getTokensLeft();

    /**
     * Stops this threads` work.
     */
    void shutdown();
}

