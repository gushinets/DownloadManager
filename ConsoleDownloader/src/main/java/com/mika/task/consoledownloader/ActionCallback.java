package com.mika.task.consoledownloader;

import java.nio.channels.FileChannel;

/**
 * Callback to execute after downloading thread is finished.
 *
 * @author Mikhail Gushinets
 * @since 01/09/2014
 */
public interface ActionCallback {
    /**
     * This method is called by downloader thread after it has
     * finished download task.
     *
     * @param out file channel that downloader thread was writing to.
     * @param bytesDownloaded bytes downloaded by downloader thread.
     */
    void perform(FileChannel out, long bytesDownloaded);
}
