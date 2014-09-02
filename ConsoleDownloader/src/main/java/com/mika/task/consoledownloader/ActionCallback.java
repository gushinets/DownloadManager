package com.mika.task.consoledownloader;

import java.nio.channels.FileChannel;

public interface ActionCallback {
    void perform(FileChannel out, long bytesDownloaded);
}
