package com.mika;


import java.io.*;
import java.nio.ByteBuffer;
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
import java.util.concurrent.*;
import java.net.*;


// Implementation of DownloadManager handles all downloads and delegates download tasks to Downloader threads
public interface DownloadManager {

    public void startDownload();    // start download process;
    public void increaseBytesDownloaded( long val );

    // register that partial download is completed and close channel if necessary
    public void downloadComplete(SeekableByteChannel channel);
}




// Concrete DownloadManager interface implementation
class DownloadManagerImpl implements DownloadManager {

    private int threadsCount;
    private long downloadSpeed;
    private String outputFolder;
    private String downloadList;
    private long totalBytesDownloaded;
    private long totalContentLength;

    private ExecutorService executorService;    // thread pool to handle download tasks
    private HashMap<SeekableByteChannel,Integer> outputFilesMap;
    private HashMap<String,String> resourcesMap;    // stores already downloaded resources and names
    private HashMap<String, ArrayList<String> > copyResourcesMap;  // stores <URL, <List of destination files>>

    private TokenBucket tokenBucket;

    private final int DOWNLOAD_BUFFER_SIZE = 4096;  // buffer size in bytes

    DownloadManagerImpl( int nThreads, long speedLimit, String outFolder, String links )
    {
        threadsCount = nThreads;
        downloadSpeed = speedLimit;
        outputFolder = outFolder;
        downloadList = links;
        totalBytesDownloaded = 0;
        totalContentLength = 0;

        executorService = Executors.newFixedThreadPool( threadsCount );
        outputFilesMap = new HashMap<SeekableByteChannel, Integer>( 1 );
        resourcesMap = new HashMap<String, String>( 1 );
        copyResourcesMap = new HashMap<String, ArrayList<String> >();

        tokenBucket = new TokenBucketImpl( downloadSpeed );
    }

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
                    String src = outputFolder + "\\" + resourcesMap.get(address);
                    String dest = outputFolder + "\\" + fileToSave;

                    if( !copyResourcesMap.containsKey( src ) ) {
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
                System.out.println("Website: " + address);
                System.out.println("Response Code: " + checkConnection.getResponseCode());
                System.out.println("Partial content retrieval support = " + (supportPartialContent ? "Yes" : "No"));
                System.out.println("Content-Length: " + contentSize);
                checkConnection.disconnect();

                totalContentLength += contentSize;

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
                        if (downloadConnection.getResponseCode() / 100 != 2) {
                            System.err.println("Unsuccessful response code:" + downloadConnection.getResponseCode());
                            break;
                        }
                        int contentLength = downloadConnection.getContentLength();
                        if (contentLength < 1) {
                            System.err.println("Can not get content");
                            break;
                        }

                        InputStream is = downloadConnection.getInputStream();
                        ReadableByteChannel rbc = Channels.newChannel(is);
                        ReadableByteChannel readChannel = ( downloadSpeed > 0 ) ? new LimitedByteChannel( rbc, this ) : rbc;

                        // create download task
                        executorService.execute(new Downloader(readChannel, outChannel, currentBlockStart, this, DOWNLOAD_BUFFER_SIZE));

                        currentBlockStart = blockEnd + 1;
                    }
                }
                else    // partial content is not supported
                {
                    outputFilesMap.put( outChannel, 1 );

                    HttpURLConnection downloadConnection = (HttpURLConnection) website.openConnection();
                    downloadConnection.setRequestMethod("GET");
                    downloadConnection.connect();
                    if (downloadConnection.getResponseCode() / 100 != 2) {
                        System.err.println("Unsuccessful response code:" + downloadConnection.getResponseCode());
                        break;
                    }
                    int contentLength = downloadConnection.getContentLength();
                    if (contentLength < 1) {
                        System.err.println("Can not get content");
                        break;
                    }

                    InputStream is = downloadConnection.getInputStream();
                    ReadableByteChannel rbc = Channels.newChannel(is);
                    ReadableByteChannel readChannel = ( downloadSpeed > 0 ) ? new LimitedByteChannel( rbc, this ) : rbc;

                    // create download task
                    executorService.execute(new Downloader(readChannel, outChannel, 0, this, DOWNLOAD_BUFFER_SIZE));
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
            for( int i = 0; i < destsList.size(); i++ ) {
                Path srcPath = FileSystems.getDefault().getPath( src );
                Path dstPath = FileSystems.getDefault().getPath( destsList.get( i ) );
                try {
                    Files.copy( srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);
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

        System.out.println("==================");
        System.out.println("Download complete");
        System.out.println("Work time: " + minutes + ":" + seconds + " (min:sec)");
        System.out.println("Totally downloaded: " + totalBytesDownloaded + " bytes");
        //System.out.println("Total content length: " + totalContentLength + " bytes");
        System.out.println("Average download speed: " + totalBytesDownloaded/(totalTime/1000) + " bytes/sec");
    }

    // register that download is completed and close channel if necessary
    synchronized public void downloadComplete( SeekableByteChannel channel )
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
                outputFilesMap.replace(channel, new Integer(curVal));
            }
        }
    }

    synchronized public void increaseBytesDownloaded( long val )
    {
        totalBytesDownloaded += val;
    }

    // All Downloaders call this method
    // Download manager decides how much data to read
    synchronized public int read( LimitedByteChannel lbc, ByteBuffer dst ) throws IOException
    {
        long tokensLeft = tokenBucket.getTokensLeft();
        int bufferSize = dst.capacity();
        int read = 0;

        if( tokensLeft < bufferSize ) {
            ByteBuffer newBuf = ByteBuffer.allocate( (int)tokensLeft ); // if tokensLeft < bufferSize we can truncate long to int
            read = lbc.reallyRead( newBuf );
            newBuf.flip();
            dst.put( newBuf );
        }
        else {
            read = lbc.reallyRead( dst );
        }

        // remove "read" tokens from bucket
        tokenBucket.getTokens( read );

        return read;
    }



}
