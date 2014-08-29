package com.mika;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class LimitedByteChannel implements ReadableByteChannel {
    private TokenBucket tokenBucket;
    private ReadableByteChannel rbc;

    public LimitedByteChannel( ReadableByteChannel original, TokenBucket bucket )
    {
        rbc = original;
        tokenBucket = bucket;
    }

    @Override
    // All Downloaders call this method
    // Channel decides how much data to read
    synchronized public int read(ByteBuffer dst) throws IOException {
        long tokensLeft = tokenBucket.getTokensLeft();
        int bufferSize = dst.capacity();
        int read = 0;

        if( tokensLeft < bufferSize ) {
            ByteBuffer newBuf = ByteBuffer.allocate( (int)tokensLeft ); // if tokensLeft < bufferSize we can truncate long to int
            read = this.reallyRead( newBuf );
            newBuf.flip();
            dst.put( newBuf );
        }
        else {
            read = this.reallyRead( dst );
        }

        // remove "read" tokens from bucket
        tokenBucket.getTokens( read );

        return read;
    }

    public int reallyRead(ByteBuffer dst) throws IOException {
        return rbc.read( dst );
    }


    @Override
    public boolean isOpen() {
        return rbc.isOpen();
    }

    @Override
    public void close() throws IOException {
        rbc.close();
    }
}
