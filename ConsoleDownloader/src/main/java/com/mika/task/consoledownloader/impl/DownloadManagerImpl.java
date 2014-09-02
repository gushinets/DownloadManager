package com.mika.task.consoledownloader.impl;

import com.mika.task.consoledownloader.*;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.springframework.util.Assert;
import org.springframework.util.StopWatch;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * Implementation of DownloadManager handles all  downloads and delegates download tasks to Downloader threads.
 */
public class DownloadManagerImpl implements DownloadManager {

    private final int threadsCount;
    private final long downloadSpeed;
    private final String outputFolder;
    private final String downloadList;
    private long totalBytesDownloaded;

    private final ExecutorService executorService;                    // thread pool to handle download tasks

    private final Map<SeekableByteChannel, Integer> outputFilesMap;
    private final Map<String, String> resourcesMap;                   // stores already downloaded resources and names
    private final Map<String, Set<String>> copyResourcesMap;          // stores <URL, <List of destination files>>

    private TokenBucket tokenBucket;

    private static final int DOWNLOAD_BUFFER_SIZE = 4096;       // buffer size in bytes
    private static final int TIME_TO_WAIT_TERMINATION = 10;     // time to wait for termination of executorService
    private static final String RANGE_BYTES_STRING = "bytes=";


    public DownloadManagerImpl( int nThreads, long speedLimit, String outFolder, String links)
    {
        Assert.isTrue(nThreads > 0, "Thread number must be positive value");
        Assert.isTrue(speedLimit >= 0, "Download speed limit must be positive value");
        Assert.notNull(outFolder, "Output folder must be not null");
        Assert.notNull(links, "Links file must be not null");

        threadsCount = nThreads;
        downloadSpeed = speedLimit;
        outputFolder = outFolder;
        downloadList = links;
        totalBytesDownloaded = 0;

        executorService = Executors.newFixedThreadPool(threadsCount);
        outputFilesMap = new HashMap<SeekableByteChannel, Integer>(1);
        resourcesMap = new HashMap<String, String>(1);
        copyResourcesMap = new HashMap<String, Set<String>>();

        if (downloadSpeed > 0) {
            tokenBucket = new TokenBucketImpl(downloadSpeed);
        }
    }

    public final void startDownload() {
        StopWatch watcher = new StopWatch();
        watcher.start();

        Thread t = null;
        if (downloadSpeed > 0) {
            t = new Thread(tokenBucket);
            t.start();
        }

        BufferedReader br = null;
        String sCurrentLine;
        try {
            br = new BufferedReader( new FileReader( downloadList ) );
            while ((sCurrentLine = br.readLine()) != null)
            {
                String [] list = sCurrentLine.split(" ");
                if( list.length < 2 ){
                    System.err.println("Too few tokens in line: " + sCurrentLine );
                    continue;
                }
                String address = list[0];
                String fileToSave = list[1];

                if( !resourceRequiresDownloading( address, fileToSave) ) {
                    continue;
                }

                downloadResourceToFile( address, fileToSave );
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (br != null)
                    br.close();
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        executorService.shutdown();

        // wait for all downloads to complete
        try {
            boolean terminated;
            do{
                terminated = executorService.awaitTermination(TIME_TO_WAIT_TERMINATION, TimeUnit.MINUTES);
            } while( !terminated );
        }catch ( InterruptedException e ) {
            e.printStackTrace();
        }

        if( t != null ) {
            tokenBucket.shutdown();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        copyDuplicateLinks();

        watcher.stop();
        long totalTime = watcher.getTotalTimeMillis();
        int minutes = (int)(totalTime/60000);
        int seconds = (int)(totalTime/1000) - 60*minutes;

        System.out.println("==================");
        System.out.println("Download complete");
        System.out.println("Work time: " + minutes + ":" + seconds + " (min:sec)");
        System.out.println("Totally downloaded: " + totalBytesDownloaded + " bytes");
        System.out.println("Average download speed: " + totalBytesDownloaded/(totalTime/1000) + " bytes/sec");
    }

    private void createDownloadTasks( String address, int blocksCount, long blockSize, boolean supportPartialContent, FileChannel outChannel ) {
        long currentBlockStart = 0;
        long blockEnd = 0;

        try {
            for (int k = 0; k < blocksCount; k++) {
                URL website = new URL(address);
                HttpURLConnection downloadConnection = (HttpURLConnection) website.openConnection();
                downloadConnection.setRequestMethod(HttpGet.METHOD_NAME);

                if (supportPartialContent) {
                    blockEnd = currentBlockStart + blockSize - 1;
                    if (k == blocksCount - 1)
                        downloadConnection.setRequestProperty(HttpHeaders.RANGE, RANGE_BYTES_STRING + currentBlockStart + "-");
                    else
                        downloadConnection.setRequestProperty(HttpHeaders.RANGE, RANGE_BYTES_STRING + currentBlockStart + "-" + blockEnd);
                }

                downloadConnection.connect();

                if (downloadConnection.getResponseCode() / 100 != 2) {
                    System.err.println("Unsuccessful response code:" + downloadConnection.getResponseCode());
                    continue;
                }
                int contentLength = downloadConnection.getContentLength();
                if (contentLength < 1) {
                    System.err.println("Can not get content");
                    continue;
                }

                InputStream is = downloadConnection.getInputStream();
                ReadableByteChannel rbc = Channels.newChannel(is);
                ReadableByteChannel readChannel = (downloadSpeed > 0) ? new LimitedByteChannel(rbc, tokenBucket) : rbc;

                // create download task
                executorService.execute(new Downloader(readChannel, outChannel, currentBlockStart, DOWNLOAD_BUFFER_SIZE,
                        new ActionCallback() {
                            public void perform(FileChannel out, long bytesDownloaded) {
                                downloadComplete(out, bytesDownloaded);
                            }

                        }
                ));

                if (supportPartialContent) {
                    currentBlockStart = blockEnd + 1;
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void downloadResourceToFile( String address, String fileToSave ) {
        try {
            // check if web server supports partial download
            URL website = new URL(address);
            HttpURLConnection checkConnection = (HttpURLConnection) website.openConnection();
            checkConnection.setRequestMethod(HttpHead.METHOD_NAME);
            checkConnection.setRequestProperty(HttpHeaders.RANGE, RANGE_BYTES_STRING + "0-");

            boolean supportPartialContent = (checkConnection.getResponseCode() == HttpStatus.SC_PARTIAL_CONTENT);
            long contentSize = checkConnection.getContentLengthLong();

            System.out.println("Website: " + address);
            //System.out.println("Response Code: " + checkConnection.getResponseCode());
            System.out.println("Partial content retrieval support = " + (supportPartialContent ? "Yes" : "No"));
            System.out.println("Content-Length: " + contentSize);
            checkConnection.disconnect();

            // if entire file size is smaller than buffer_size, then download it in one thread
            if( contentSize <= DOWNLOAD_BUFFER_SIZE ) {
                supportPartialContent = false;
            }

            int blocksCount = 1;    // if partial content is not supported
            long blockSize = 0;

            if( supportPartialContent ) {
                blocksCount = threadsCount;
                blockSize = (int) contentSize / blocksCount + 1;

                if (blockSize < DOWNLOAD_BUFFER_SIZE) {
                    blockSize = DOWNLOAD_BUFFER_SIZE;

                    if (contentSize % blockSize > 0)
                        blocksCount = (int) (contentSize / blockSize) + 1;
                    else
                        blocksCount = (int) (contentSize / blockSize);
                }
            }

            RandomAccessFile aFile = new RandomAccessFile(outputFolder + File.separator + fileToSave, "rw");
            FileChannel outChannel = aFile.getChannel();

            // save FileChannel to close it after all downloads complete
            outputFilesMap.put( outChannel, blocksCount );

            createDownloadTasks( address, blocksCount, blockSize, supportPartialContent, outChannel );
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean resourceRequiresDownloading( String address, String fileToSave ) {
        boolean requiresDownload;
        if( !resourcesMap.containsKey(address) ) {
            resourcesMap.put(address, fileToSave);
            requiresDownload = true;
        }
        else {
            String src = outputFolder + File.separator + resourcesMap.get(address);
            String dest = outputFolder + File.separator + fileToSave;

            if( !copyResourcesMap.containsKey( src ) ) {
                Set<String> destsList = new HashSet<String>();
                destsList.add( dest );
                copyResourcesMap.put( src, destsList );
            }
            else {
                copyResourcesMap.get( src ).add( dest );
            }

            requiresDownload = false;
        }

        return requiresDownload;
    }

    private void copyDuplicateLinks() {
        Iterator<Map.Entry<String, Set<String>>> it = copyResourcesMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Set<String>> pairs = (Map.Entry<String, Set<String>>)it.next();
            //System.out.println(pairs.getKey() + " = " + pairs.getValue());
            String src = pairs.getKey();
            Set<String> destsList = pairs.getValue();

            for (String aDestsList : destsList) {
                Path srcPath = FileSystems.getDefault().getPath(src);
                Path dstPath = FileSystems.getDefault().getPath(aDestsList);
                try {
                    Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            it.remove();
        }
    }

    // register that partial download is completed and close channel if necessary
    synchronized public void downloadComplete( SeekableByteChannel channel, long bytesDownloaded )
    {
        Assert.notNull( channel, "Channel reference must be not null" );
        Assert.isTrue( bytesDownloaded >= 0, "Bytes downloaded can not be negative");

        if( outputFilesMap.containsKey( channel ) ) {
            Integer curVal = outputFilesMap.get(channel);
            curVal--;
            if (curVal == 0) {
                outputFilesMap.remove(channel);

                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                outputFilesMap.put( channel, curVal);
            }
        }

        totalBytesDownloaded += bytesDownloaded;
    }

}
