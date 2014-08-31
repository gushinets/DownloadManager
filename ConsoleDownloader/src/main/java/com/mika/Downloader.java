package com.mika;


import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;


public class Downloader implements Runnable{
    private ReadableByteChannel rbc;
    private FileChannel outChannel;
    private long position;
    private int bufferSize;
    private long totalBytesRead;

    ActionCallback actionCallback;


    public Downloader(ReadableByteChannel readChannel, FileChannel writeChannel, long offset, int bufSize, ActionCallback actCallback){
        Assert.notNull( readChannel, "Channel to read from must be not null");
        Assert.notNull( writeChannel, "Channel to write to must be not null");
        Assert.isTrue(offset >= 0, "Offset must be non-negative value");
        Assert.isTrue( bufSize > 0, "Read buffer size must be positive value");

        rbc = readChannel;
        outChannel = writeChannel;
        position = offset;
        bufferSize = bufSize;
        totalBytesRead = 0;

        actionCallback = actCallback;
    }

    @Override
    public void run()
    {
        // TODO: implement bytes transfer without ByteBuffer (using transferFrom() method) and compare speed
        try
        {
            ByteBuffer buf = ByteBuffer.allocate( bufferSize );
            int bytesRead = rbc.read(buf);
            long curPos = position;
            while (bytesRead != -1)
            {
                totalBytesRead += bytesRead;

                buf.flip();  //make buffer ready for read

                while(buf.hasRemaining()){
                    int bytesWritten = outChannel.write( buf, curPos );
                    if( bytesWritten > 0)
                        curPos += bytesWritten;
                }

                buf.clear(); //make buffer ready for writing
                bytesRead = rbc.read(buf);
            }

            rbc.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        if( actionCallback != null ) {
            actionCallback.perform(outChannel, totalBytesRead);
        }
    }
}
