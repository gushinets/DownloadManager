package com.mika.task.consoledownloader;

import com.mika.task.consoledownloader.impl.DownloadManagerImpl;
import org.apache.commons.cli.*;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Main class of console downloader.
 *
 * @author Mikhail Gushinets
 * @since 01/09/2014
 */
public class Main {
    /**
     * Default number of downloading threads.
     */
    private static final int DEFAULT_THREADS_COUNT = 5;

    /**
     * Default value for download speed limit.
     * 0 means limitless.
     */
    private static final long DEFAULT_SPEED_LIMIT = 0;

    /**
     * Default name for file with links.
     */
    private static final String DEFAULT_LINKS_FILE = "links.txt";

    /**
     * Logger to log messages.
     */
    private static final ch.qos.logback.classic.Logger LOGGER =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Main.class);

    /**
     * Main method of main class.
     * @param args input parameters for program
     */
    public static void main(String[] args) {
        String nThreads = "n";
        String sLim = "l";
        String oFol = "o";
        String inFile = "f";

        Options options = new Options();
        options.addOption(OptionBuilder.isRequired(false).hasArg(true).withDescription("Download threads count").create(nThreads));
        options.addOption(OptionBuilder.isRequired(false).hasArg(true).withDescription("Download speed limit").create(sLim));
        options.addOption(OptionBuilder.isRequired(true).hasArg(true).withDescription("Output folder").create(oFol));
        options.addOption(OptionBuilder.isRequired(false).hasArg(true).withDescription("Path to file with download links").create(inFile));

        int threadsCount = DEFAULT_THREADS_COUNT;
        long downloadSpeed = DEFAULT_SPEED_LIMIT;
        String outputFolder = null;
        String downloadList = DEFAULT_LINKS_FILE;

        final int BYTES_IN_KB = 1024;
        final int BYTES_IN_MB = 1024 * 1024;

        CommandLineParser parser = new BasicParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption(nThreads)) {
                threadsCount = Integer.valueOf(cmd.getOptionValue(nThreads));

                if (threadsCount <= 0) {
                    LOGGER.error("Threads count should be positive value");
                    System.exit(1);
                }
            }

            if (cmd.hasOption(sLim)) {
                String val = cmd.getOptionValue(sLim);
                int multiplier = 1;
                int k = val.length() - 1;

                char suffix = val.charAt(k);
                switch (suffix) {
                    case 'k':
                        multiplier = BYTES_IN_KB;
                        break;
                    case 'm':
                        multiplier = BYTES_IN_MB;
                        break;
                    default:
                        if (!Character.isDigit(suffix)) {
                            LOGGER.error("Incorrect suffix for speed limit specified");
                            System.exit(1);
                        }
                        break;
                }

                String speedVal = val.substring(0, k);
                downloadSpeed = Long.valueOf(speedVal) * multiplier;
                if (downloadSpeed < 0) {
                    LOGGER.error("Download speed should not be negative");
                    System.exit(1);
                }
            }

            if (cmd.hasOption(oFol)) {
                outputFolder = cmd.getOptionValue(oFol);

                File f = new File(outputFolder);
                if (!f.exists() || !f.isDirectory()) {
                    LOGGER.error("Incorrect output folder specified");
                    System.exit(1);
                }
            }

            if (cmd.hasOption(inFile)) {
                downloadList = cmd.getOptionValue(inFile);

                File f = new File(downloadList);
                if (!f.exists() || !f.isFile()) {
                    LOGGER.error("Incorrect links file specified");
                    System.exit(1);
                }
            }
        } catch (ParseException exp) {
            LOGGER.error("Parsing failed.  Reason: {}", exp.getMessage());
            System.exit(1);
        }

        DownloadManager dm = new DownloadManagerImpl(threadsCount, downloadSpeed, outputFolder, downloadList);
        dm.startDownload();
    }
}
