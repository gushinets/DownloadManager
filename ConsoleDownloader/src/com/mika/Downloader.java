package com.mika;


import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class Downloader implements Runnable{
    private ReadableByteChannel rbc;
    private FileChannel outChannel;
    private long position;
    private int bufferSize;
    private DownloadManager downloadManager;


    public Downloader( ReadableByteChannel readChannel, FileChannel writeChannel, long offset, DownloadManager dm, int bufSize ){
        rbc = readChannel;
        outChannel = writeChannel;
        position = offset;
        downloadManager = dm;
        bufferSize = bufSize;
    }

    @Override
    public void run()
    {
        if( rbc == null || outChannel == null || position < 0 )
            return;

        // TODO: implement bytes transfer without ByteBuffer (using transferFrom() method) and compare speed
        try
        {
            ByteBuffer buf = ByteBuffer.allocate( bufferSize );
            int bytesRead = rbc.read(buf);
            long curPos = position;
            while (bytesRead != -1)
            {
                downloadManager.increaseBytesDownloaded( bytesRead );
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

        downloadManager.downloadComplete( outChannel );
    }
}
