package com.mika;

import java.nio.channels.FileChannel;

public interface ActionCallback {
    void perform( FileChannel out, long bytesDownloded );
}
