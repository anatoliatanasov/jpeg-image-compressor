package name.atanasov.home.jpegcompressor;

import name.atanasov.home.jpegcompressor.logging.JpegCompressorLogFormatter;
import name.atanasov.home.jpegcompressor.processing.IStageProcessor;
import name.atanasov.home.jpegcompressor.processing.ImageCompressionQueue;
import name.atanasov.home.jpegcompressor.processing.JpegImageCompressionMessage;
import name.atanasov.home.jpegcompressor.processing.compressing.ImageCompressor;
import name.atanasov.home.jpegcompressor.processing.loading.FilesLoader;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;

/**
 * Starts the JPEG Compressor application.
 * First the prepareEnvironment() method has to be invoked and then startApplication.
 */
public final class Starter {
    private static final Logger logger = Logger.getLogger(Starter.class.getName());
    private static Starter instance = null;

    private final static ReentrantLock lock = new ReentrantLock(true);
    private boolean envrinmentPrepared = false;
    private boolean applicationRunning = false;
    private CommandLine cliArgs = null;
    private Options cliOptions = null;

    /**
     * Singleton factory method
     * @return
     */
    public static Starter getInstance() {
        lock.lock();
        try {
            if(instance == null) {
                instance = new Starter();
            }
        } finally {
            lock.unlock();
        }

        return instance;
    }

    /**
     * Prepares the environment by:
     *  1. setting up the Apache Commons CLI Options
     *  2. parsing the provided Java args
     *  3. configure the Java logging facility
     * @param javaAppArgs
     */
    public void prepareEnvironment(String[] javaAppArgs) {
        lock.lock();
        try {
            setupCliOptions();

            CommandLineParser cliParser = new DefaultParser();

            this.cliArgs = cliParser.parse(this.cliOptions, javaAppArgs);
            this.cliArgs.getParsedOptionValue("srcfolder");
            this.cliArgs.getParsedOptionValue("compressionratio");
            this.cliArgs.getParsedOptionValue("compressionthreads");
            logger.fine("Successfully parsed CLI arguments!");

            if(this.cliArgs.hasOption("help")) {
                printHelpMessage();
                return;
            }

            configureLogging();
            this.envrinmentPrepared = true;

            logger.fine("Successfully prepared environment!");
        } catch(ParseException pe) {
            logger.warning("Unable to parse application parameters!");
            throw new IllegalArgumentException("Error parsing application parameters!", pe);
        } finally {
            lock.unlock();
        }
    }

    public String getCliArgument(String argumentName) {
        lock.lock();
        try {
            if(this.cliArgs.hasOption(argumentName)) {
                return this.cliArgs.getOptionValue(argumentName) == null ? "" : this.cliArgs.getOptionValue(argumentName);
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void startApplication() {
        lock.lock();
        if(!this.envrinmentPrepared) {
            logger.warning("Environment is not configured! Please, invoke prepareEnvironment() first!");
            return;
        }
        lock.unlock();

        lock.lock();
        try {
            if(this.applicationRunning) {
                throw new IllegalStateException("Application is already running! Please, wait till it finishes!");
            }
            this.applicationRunning = true;
        } finally {
            lock.unlock();
        }

        File srcFolder = new File(getCliArgument("srcfolder"));
        if(!srcFolder.exists() || !srcFolder.isDirectory()) {
            logger.severe("Source folder: [" + getCliArgument("srcfolder") +
                            "] does not exists or is not a valid directory!");
            lock.lock();
            this.applicationRunning = false;
            lock.unlock();

            return;
        }

        boolean recursively = getCliArgument("recursively") == null ? false : true;

        List<Callable<Integer>> tasks = new ArrayList<Callable<Integer>>(2);
        tasks.add(() -> {
            IStageProcessor loader = new FilesLoader(srcFolder,
                    recursively);
            loader.setMessageQueue(ImageCompressionQueue.getInstance());
            loader.process();
            return 0;
        });

        final String compressionThreads = getCliArgument("compressionthreads");
        Integer numberOfThreads = compressionThreads == null ? null : Integer.valueOf(compressionThreads);

        final String compressionRatio = getCliArgument("compressionratio");
        Float compRatio = compressionRatio == null ? null : Float.valueOf(compressionRatio);
        tasks.add(() -> {
            IStageProcessor compressor = new ImageCompressor(numberOfThreads, compRatio);
            compressor.setMessageQueue(ImageCompressionQueue.getInstance());
            compressor.process();
            return 0;
        });


        try {
            ExecutorService executorService = Executors.newCachedThreadPool();
            List<Future<Integer>> results = executorService.invokeAll(tasks);

            int filesLoaded = ((Integer)results.get(0).get()).intValue();


            int filesCompressed = ((Integer)results.get(1).get()).intValue();


            assert filesLoaded == filesCompressed;

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch(ExecutionException ee) {
            ee.printStackTrace();
        }


        lock.lock();
        this.applicationRunning = false;
        lock.unlock();
    }



    private Starter() {

    }


    private void setupCliOptions() {
        Option srcFolder = Option.builder("srcfolder")
                .argName("srcfolder")
                .desc("JPEG image compressor source fodler to start looking for JPEG images.")
                .required(true)
                .type(File.class)
                .numberOfArgs(1)
                .build();

        Option recursively = Option.builder("recursively")
                .argName("recursively")
                .desc("Flag indicating if the JPEG image loading will go recursively under " +
                        "the src-folder.")
                .numberOfArgs(0)
                .build();

        Option logFile = Option.builder("logfile")
                .argName("logfile")
                .desc("File name that will be created in the current working directory " +
                        "and will contain the detailed log of the application execution.")
                .numberOfArgs(1)
                .build();

        Option compressionRatio = Option.builder("compressionratio")
                .argName("compressionratio")
                .desc("JPEG compression ratio to compress images with.")
                .numberOfArgs(1)
                .type(Float.class)
                .build();

        Option numberOfCompressionThreads = Option.builder("compressionthreads")
                .argName("compressionthreads")
                .desc("Number of parallel threads that will handle the images compression.")
                .numberOfArgs(1)
                .type(Integer.class)
                .build();

        Option help = Option.builder("help")
                .argName("help")
                .desc("Prints this message.")
                .numberOfArgs(0)
                .build();

        OptionGroup helpGroup = new OptionGroup();
        helpGroup.addOption(help).addOption(srcFolder);

        Options options = new Options();
        options.addOption(srcFolder);
        options.addOption(help);
        options.addOption(recursively);
        options.addOption(logFile);
        options.addOption(compressionRatio);
        options.addOption(numberOfCompressionThreads);

        logger.fine("Successfully configured command line interface arguments!");

        this.cliOptions = options;
    }


    private void printHelpMessage() {
        String header = "Compresses JPEG files located under the srcfolder directory.\n\n";

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("JPEG Compressor", header, this.cliOptions, null, true);
    }

    private void configureLogging() {
        Logger logger = Logger.getLogger(getClass().getPackage().getName());

        Handler console = new ConsoleHandler();
        console.setFormatter(new JpegCompressorLogFormatter());
        console.setLevel(Level.INFO);

        logger.addHandler(console);

        try {
            if(cliArgs.hasOption("logfile")) {
                final String logFileName = cliArgs.getOptionValue("logfile");
                StringBuilder logFileNamePath = new StringBuilder(Paths.get(".").toAbsolutePath().normalize().toString())
                                            .append(File.separator)
                                            .append(logFileName);

                FileHandler file = new FileHandler(logFileNamePath.toString(), false);
                file.setFormatter(new JpegCompressorLogFormatter());
                file.setLevel(Level.FINEST);

                logger.addHandler(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.setLevel(Level.FINEST);
        logger.setUseParentHandlers(false);

        logger.fine("Successfully configured Java logging facility!");


    }
}
