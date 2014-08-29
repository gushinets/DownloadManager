package com.mika.impl;


import com.mika.*;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


// избавься от строковых http значений типа HEAD, Range (используй httpcomponents-core, у них все константы есть https://hc.apache.org/httpcomponents-core-4.3.x/httpcore/apidocs/constant-values.html)

// Implementation of DownloadManager handles all downloads and delegates download tasks to Downloader threads
public class DownloadManagerImpl implements DownloadManager {

    private int threadsCount;
    private long downloadSpeed;
    private String outputFolder;
    private String downloadList;
    private long totalBytesDownloaded;
    private long totalContentLength;

    private ExecutorService executorService;    // thread pool to handle download tasks

    private Map<SeekableByteChannel,Integer> outputFilesMap;
    private Map<String,String> resourcesMap;    // stores already downloaded resources and names
    private Map<String, ArrayList<String>> copyResourcesMap;  // stores <URL, <List of destination files>>

    private TokenBucket tokenBucket;

    private static final int DOWNLOAD_BUFFER_SIZE = 4096;  // buffer size in bytes


    public DownloadManagerImpl(int nThreads, long speedLimit, String outFolder, String links)
    {
        // нужно проверять на ошибки, можешь http://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/Assert.html
        // Assert.isTrue(nThreads > 0, "Thread number must be positive");

        threadsCount = nThreads;
        downloadSpeed = speedLimit;
        outputFolder = outFolder;
        downloadList = links;
        totalBytesDownloaded = 0;
        totalContentLength = 0;

        executorService = Executors.newFixedThreadPool(threadsCount);
        outputFilesMap = new HashMap<SeekableByteChannel, Integer>( 1 );
        resourcesMap = new HashMap<String, String>( 1 );
        copyResourcesMap = new HashMap<String, ArrayList<String> >();

        tokenBucket = new TokenBucketImpl( downloadSpeed );
    }

    // тяжеловато читать основной метод
    // Можно разнести функциональность по логике
    public void startDownload()
    {
        long startTime = System.currentTimeMillis();

        Thread t = null;
        if( downloadSpeed > 0 ) {
            t = new Thread(tokenBucket);
            t.start();
        }

        BufferedReader br = null;
        String sCurrentLine = null;
        try
        {
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

                if( !resourcesMap.containsKey(address) ) {
                    resourcesMap.put(address, fileToSave);
                }
                else {
                    // используй константу File.separator
                    String src = outputFolder + "\\" + resourcesMap.get(address);
                    String dest = outputFolder + "\\" + fileToSave;

                    if( !copyResourcesMap.containsKey( src ) ) {
                        // Set правильнее, чтобы один и тот же файл несолько раз не копировать
                        ArrayList<String> destsList = new ArrayList<String>();
                        destsList.add( dest );
                        copyResourcesMap.put( src, destsList );
                    }
                    else {
                        copyResourcesMap.get( src ).add( dest );
                    }

                    continue;
                }

                // check if webserver supports partial download
                URL website = new URL( address );
                HttpURLConnection checkConnection = (HttpURLConnection)website.openConnection();
                checkConnection.setRequestMethod("HEAD");
                checkConnection.setRequestProperty("Range", "bytes=0-");

                boolean supportPartialContent = checkConnection.getResponseCode() == HttpURLConnection.HTTP_PARTIAL;
                long contentSize = checkConnection.getContentLengthLong();
                // дебаг информация , как подключишь loging фреймворк сделай у него северити DEBUG
                System.out.println("Website: " + address);
                System.out.println("Response Code: " + checkConnection.getResponseCode());
                System.out.println("Partial content retrieval support = " + (supportPartialContent ? "Yes" : "No"));
                System.out.println("Content-Length: " + contentSize);
                checkConnection.disconnect();

                totalContentLength += contentSize;

                // используй константу File.separator
                RandomAccessFile aFile = new RandomAccessFile(outputFolder + "\\" + fileToSave, "rw");
                FileChannel outChannel = aFile.getChannel();

                // if entire file size is smaller than buffer_size, then download it in one thread
                if( contentSize <= DOWNLOAD_BUFFER_SIZE )
                    supportPartialContent = false;

                if( supportPartialContent ) {
                    int blocksCount = threadsCount;
                    long currentBlockStart = 0;
                    long blockSize = (int) contentSize / blocksCount + 1;

                    if( blockSize < DOWNLOAD_BUFFER_SIZE ) {
                        blockSize = DOWNLOAD_BUFFER_SIZE;

                        if( contentSize%blockSize > 0 )
                            blocksCount = (int)(contentSize/blockSize) + 1;
                        else
                            blocksCount = (int)(contentSize/blockSize);
                    }

                    // save FileChannel to close it after all downloads complete
                    outputFilesMap.put( outChannel, blocksCount );

                    for (int k = 0; k < blocksCount; k++) {
                        long blockEnd = currentBlockStart + blockSize - 1;
                        HttpURLConnection downloadConnection = (HttpURLConnection) website.openConnection();
                        downloadConnection.setRequestMethod("GET");
                        if (k == blocksCount - 1)
                            downloadConnection.setRequestProperty("Range", "bytes=" + currentBlockStart + "-");
                        else
                            downloadConnection.setRequestProperty("Range", "bytes=" + currentBlockStart + "-" + blockEnd);
                        downloadConnection.connect();
                        // это че за дикая проверка ? Статус 200 - это известный HTTP респонс код
                        // https://hc.apache.org/httpcomponents-core-4.3.x/httpcore/apidocs/org/apache/http/HttpStatus.html#SC_OK
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
                        ReadableByteChannel readChannel = ( downloadSpeed > 0 ) ? new LimitedByteChannel( rbc, tokenBucket ) : rbc;

                        // create download task
                        //executorService.execute(new Downloader(readChannel, outChannel, currentBlockStart, this, DOWNLOAD_BUFFER_SIZE));

                        executorService.execute(new Downloader( readChannel, outChannel, currentBlockStart, this, DOWNLOAD_BUFFER_SIZE,
                                new ActionCallback() {
                                    public void perform( FileChannel out, long bytesDownloded ) {
                                        downloadComplete( out, bytesDownloded );
                                    }

                                }));

                        currentBlockStart = blockEnd + 1;
                    }
                }
                else    // partial content is not supported
                {
                    // дубирование кода, подумай как вынести в отдельный метод и заиспользовать в обоих случаях
                    outputFilesMap.put( outChannel, 1 );

                    HttpURLConnection downloadConnection = (HttpURLConnection) website.openConnection();
                    downloadConnection.setRequestMethod("GET");
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
                    ReadableByteChannel readChannel = ( downloadSpeed > 0 ) ? new LimitedByteChannel( rbc, tokenBucket ) : rbc;

                    // create download task
                    executorService.execute(new Downloader( readChannel, outChannel, 0, this, DOWNLOAD_BUFFER_SIZE,
                            new ActionCallback() {
                                public void perform( FileChannel out, long bytesDownloded ) {
                                    downloadComplete( out, bytesDownloded );
                                }

                            }));

                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
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
            boolean terminated = false;
            do{
                // 10 в отдельную константу
                terminated = executorService.awaitTermination(10, TimeUnit.MINUTES);
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

        Iterator it = copyResourcesMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            System.out.println(pairs.getKey() + " = " + pairs.getKey());

            String src = (String)pairs.getKey();
            ArrayList<String> destsList = (ArrayList<String>)pairs.getValue();
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




        long endTime   = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        int minutes = (int)(totalTime/60000);
        int seconds = (int)(totalTime/1000) - 60*minutes;

        // посмотри на http://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/StopWatch.html
        System.out.println("==================");
        System.out.println("Download complete");
        System.out.println("Work time: " + minutes + ":" + seconds + " (min:sec)");
        System.out.println("Totally downloaded: " + totalBytesDownloaded + " bytes");
        //System.out.println("Total content length: " + totalContentLength + " bytes");
        System.out.println("Average download speed: " + totalBytesDownloaded/(totalTime/1000) + " bytes/sec");
    }

    // register that partial download is completed and close channel if necessary
    synchronized public void downloadComplete( SeekableByteChannel channel, long bytesDownloded )
    {
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
                // избавься от replace метода, он с JDK 8 только появился
                outputFilesMap.replace(channel, curVal);
            }
        }

        totalBytesDownloaded += bytesDownloded;
    }

}
