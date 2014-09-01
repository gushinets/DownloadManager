package com.mika;


import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class LimitedByteChannel implements ReadableByteChannel {
    private TokenBucket tokenBucket;
    private ReadableByteChannel rbc;

    public LimitedByteChannel( ReadableByteChannel original, TokenBucket bucket )
    {
        Assert.notNull( original, "Channel to read from must be not null" );
        Assert.notNull( bucket, "Bucket object must be not null" );

        rbc = original;
        tokenBucket = bucket;
    }

    @Override
    // All Downloaders call this method
    // Channel decides how much data to read
    synchronized public int read(ByteBuffer dst) throws IOException {
        Assert.notNull( dst, "Buffer to read into can not be null" );

        long tokensLeft = tokenBucket.getTokensLeft();
        int bufferSize = dst.capacity();
        int read;

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
        if( read > 0 ) {
            tokenBucket.getTokens(read);
        }

        return read;
    }

    private int reallyRead(ByteBuffer dst) throws IOException {
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
