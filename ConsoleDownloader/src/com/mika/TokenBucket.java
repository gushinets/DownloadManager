package com.mika;


public interface TokenBucket extends Runnable {
    boolean getTokens( long n ); // removes n tokens from bucket
    long getTokensLeft();        // returns tokens currently left in the bucket
    void shutdown();
}

