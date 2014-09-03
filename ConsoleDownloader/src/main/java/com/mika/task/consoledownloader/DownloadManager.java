package com.mika.task.consoledownloader;

/**
 * Download manager that handles download process, creates download tasks and
 * delegates these tasks to downloading threads.
 *
 * @author Mikhail Gushinets
 * @since 01/09/2014
 */
public interface DownloadManager {
    /**
     * Starts download process.
     */
    void startDownload();
}
