package com.mika;

import java.io.File;

public class Main
{
    private static final int DEFAULT_THREADS_COUNT = 5;
    private static final long DEFAULT_SPEED_LIMIT = 0; // limitless
    private static final String DEFAULT_LINKS_FILE = "links.txt";

    // static и public местами перепутаны
    static public void main( String args[] ) throws Exception
    {
        // TODO: try using commons CLI parser here
        if( args.length != 8 )
        {
            // хороший стиль использовать имплементацию slf4j такую как (logback или log4j) для вывода сообщений куда бы то ни было
            // logback - http://logback.qos.ch/, в градл добавляешь зависимость и вперед
            System.err.println( "Too few arguments");
            System.err.println( "Usage: java -jar utility.jar -n 5 -l 2000k -o output_folder -f links.txt" );
            System.exit( 1 );
        }

        int threadsCount = DEFAULT_THREADS_COUNT;
        long downloadSpeed = DEFAULT_SPEED_LIMIT;
        String outputFolder = null;
        String downloadList = DEFAULT_LINKS_FILE;

        // parse args list
        // если воспользуешься apache commons CLI - избавишься от этого кода :)
        for( int i = 0; i < args.length; i+=2 )
        {
            String arg = args[i];
            String val = args[i+1];

            if( arg.equalsIgnoreCase("-n") ){
                threadsCount = Integer.valueOf( val );
                if( threadsCount <= 0 )
                {
                    System.err.println( "Threads count should be positive value");
                    System.exit( 1 );
                }
            }
            else if( arg.equalsIgnoreCase("-l") ) {
                int multiplier = 1;
                boolean stopFlag = false;
                int k = val.length()-1;
                // не понял почему не просто брать последний символ ? mm или mk и т.д.  - это должно быть опечаткой, т.е. ошибкой
                for(; k >= 0; k--)
                {
                    char suffix = val.charAt( k );
                    switch ( suffix )
                    {
                        case 'k': multiplier *= 1024; break;
                        case 'm': multiplier *= 1024*1024; break;
                        default: stopFlag = true; break;
                    }

                    if( stopFlag ) break;
                }

                String speedVal = val.substring(0,k+1);
                // можно обработать и показывать как ошибка, как ты это делаешь выше
                downloadSpeed = Long.valueOf( speedVal )*multiplier;

                if( downloadSpeed < 0 )
                {
                    System.err.println( "Download speed should not be negative");
                    System.exit( 1 );
                }
            }
            else if( arg.equalsIgnoreCase("-o") ) {
                outputFolder = val;

                File f = new File(outputFolder);
                if( !f.exists() || !f.isDirectory() )
                {
                    System.err.println( "Incorrect output folder specified");
                    System.exit( 1 );
                }
            }
            else if( arg.equalsIgnoreCase("-f") ) {
                downloadList = val;

                File f = new File(downloadList);
                if( !f.exists() || !f.isFile() )
                {
                    System.err.println( "Incorrect links file specified");
                    System.exit( 1 );
                }
            }
        }

        DownloadManager dm = new DownloadManagerImpl( threadsCount, downloadSpeed, outputFolder, downloadList );
        dm.startDownload();
    }
}
