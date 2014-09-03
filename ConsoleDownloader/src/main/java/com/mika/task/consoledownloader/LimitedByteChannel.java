package com.mika.task.consoledownloader;


import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Channel implementation that can limit download speed.
 * Downloading threads just read from this channel as usual
 * and channel decides how many bytes to read.
 *
 * @author Mikhail Gushinets
 * @since 01/09/2014
 */
public class LimitedByteChannel implements ReadableByteChannel {
    /**
     * Object that implements TokenBucket interface.
     */
    private final TokenBucket tokenBucket;

    /**
     * Channel to read from.
     */
    private final ReadableByteChannel rbc;

    /**
     * Constructor.
     *
     * @param original Channel to read from.
     * @param bucket TokenBucket implementation.
     */
    public LimitedByteChannel(ReadableByteChannel original, TokenBucket bucket)
    {
        Assert.notNull(original, "Channel to read from must be not null");
        Assert.notNull(bucket, "Bucket object must be not null");

        rbc = original;
        tokenBucket = bucket;
    }

    @Override
    /**
     * All Download threads call this method.
     * Channel decides how many data to read.
     */
    synchronized public int read(ByteBuffer dst) throws IOException {
        Assert.notNull(dst, "Buffer to read into can not be null");

        long tokensLeft = tokenBucket.getTokensLeft();
        int bufferSize = dst.capacity();
        int read;

        if (tokensLeft < bufferSize) {
            ByteBuffer newBuf = ByteBuffer.allocate((int) tokensLeft); // if tokensLeft < bufferSize we can truncate long to int
            read = this.reallyRead(newBuf);
            newBuf.flip();
            dst.put(newBuf);
        } else {
            read = this.reallyRead(dst);
        }

        // remove "read" tokens from bucket
        if (read > 0) {
            tokenBucket.getTokens(read);
        }

        return read;
    }

    private int reallyRead(ByteBuffer dst) throws IOException {
        return rbc.read(dst);
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
