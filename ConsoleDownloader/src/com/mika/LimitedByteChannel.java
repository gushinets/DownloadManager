package com.mika;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class LimitedByteChannel implements ReadableByteChannel {
    private DownloadManagerImpl manager;
    private ReadableByteChannel rbc;

    public LimitedByteChannel( ReadableByteChannel original, DownloadManagerImpl dm )
    {
        rbc = original;
        manager = dm;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return manager.read( this, dst );
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
