package com.mika;


// разнеси по разным файлам интерфейс и реализацию
// убери public из определения методов в интерфейсе

// Simple implementation of TokenBucket algorithm for traffic shaping
// Refer to http://en.wikipedia.org/wiki/Token_bucket

public interface TokenBucket extends Runnable {
    public boolean getTokens( long n ); // removes n tokens from bucket
    public long getTokensLeft();        // returns tokens currently left in the bucket
    public void shutdown();
}


class TokenBucketImpl implements TokenBucket {

    private long speedLimit;
    private long currentTokensCount;
    private volatile boolean keepAlive;

    TokenBucketImpl( long bytesPerSecond ) {
        speedLimit = bytesPerSecond;
        keepAlive = true;
    }

    public void shutdown() {
        keepAlive = false;
    }

    @Override
    public void run() {

        // Это че за прикол ? :)
        // Сам выбросил исключение, сам его обработал и пошел дальше
        if( speedLimit <= 0 )
            try {
                throw new Exception("Speed Limit must be positive value");
            } catch (Exception e) {
                e.printStackTrace();
            }

        // every 1 second refresh bandwidth to desired value
        while( keepAlive ) {
            currentTokensCount = speedLimit;

            try {
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean getTokens(long n) {
        if( n <= currentTokensCount ) {
            currentTokensCount -= n;
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public long getTokensLeft() {
        return currentTokensCount;
    }
}
