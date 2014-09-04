package com.mika.task.consoledownloader;


import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

/**
 * Downloading thread that handles download task.
 *
 * @author Mikhail Gushinets
 * @since 01/09/2014
 */
public class Downloader implements Runnable {
    /**
     * Channel to read from.
     */
    private final ReadableByteChannel rbc;

    /**
     * Channel to write to.
     */
    private final FileChannel outChannel;

    /**
     * Offset in output file to start writing from.
     */
    private final long position;

    /**
     * Buffer size to read into.
     */
    private final int bufferSize;

    /**
     * Bytes totally read by this thread.
     */
    private long totalBytesRead;

    /**
     * Method to call after download is finished.
     */
    private final ActionCallback actionCallback;

    /**
     * Logger to log messages.
     */
    private static final ch.qos.logback.classic.Logger LOGGER =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Downloader.class);


    /**
     * Constructor for downloading thread.
     *
     * @param readChannel Channel to read from.
     * @param writeChannel Channel to write to.
     * @param offset Offset in output file to start writing from.
     * @param bufSize Buffer size in bytes to read into.
     * @param actCallback Method to call after download is finished.
     */
    public Downloader(ReadableByteChannel readChannel, FileChannel writeChannel, long offset, int bufSize, ActionCallback actCallback) {
        Assert.notNull(readChannel, "Channel to read from must be not null");
        Assert.notNull(writeChannel, "Channel to write to must be not null");
        Assert.isTrue(offset >= 0, "Offset must be non-negative value");
        Assert.isTrue(bufSize > 0, "Read buffer size must be positive value");

        rbc = readChannel;
        outChannel = writeChannel;
        position = offset;
        bufferSize = bufSize;
        totalBytesRead = 0;

        actionCallback = actCallback;
    }

    @Override
    public void run() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(bufferSize);
            LOGGER.debug("I am starting the download");
            int bytesRead = rbc.read(buf);
            long curPos = position;
            while (bytesRead != -1) {
                totalBytesRead += bytesRead;

                buf.flip();  //make buffer ready for read

                while (buf.hasRemaining()) {
                    int bytesWritten = outChannel.write(buf, curPos);
                    if (bytesWritten > 0) {
                        curPos += bytesWritten;
                    }
                }

                buf.clear(); //make buffer ready for writing
                bytesRead = rbc.read(buf);
                if (bytesRead == -1) {
                    LOGGER.debug("Bytes read {}", bytesRead);
                }
            }

            rbc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (actionCallback != null) {
            LOGGER.debug("I am going to finish my task");
            actionCallback.perform(outChannel, totalBytesRead);
        }
    }
}
