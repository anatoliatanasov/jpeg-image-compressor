package name.atanasov.home.jpegcompressor.processing.loading;

import name.atanasov.home.jpegcompressor.processing.ImageCompressionQueue;
import name.atanasov.home.jpegcompressor.processing.JpegImageCompressionMessage;
import name.atanasov.home.jpegcompressor.processing.IStageProcessor;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Created by anatoli on 8/3/16.
 */
public class FilesLoader implements IStageProcessor {
    private static final Logger logger = Logger.getLogger(FilesLoader.class.getName());

    private Lock lock = new ReentrantLock(true);
    private ImageCompressionQueue processingQueue = null;
    private File rootFolder = null;
    private boolean loadRecursively = false;
    private int messagesProduced = 0;

    public FilesLoader(File rootFolder, boolean loadRecursively) {
        this.rootFolder = rootFolder;
        this.loadRecursively = loadRecursively;
    }

    @Override
    public void setMessageQueue(ImageCompressionQueue queue) {
        this.processingQueue = queue;
    }

    @Override
    public void process() {
        lock.lock();
        this.messagesProduced = 0;
        lock.unlock();
        long before = System.currentTimeMillis();

        loadJpegFiles(this.rootFolder, this.loadRecursively, null);
        try {
            processingQueue.put(new JpegImageCompressionMessage(null, true));
            logger.fine("Posted interruption message to stop dequeueing!");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long after = System.currentTimeMillis();
        logger.info("Loading process loaded: [" + this.messagesProduced + "] files.");
        logger.info("Loading process finished for: [" + (after - before) + "] ms");

    }

    private void loadJpegFiles(File srcFolder, boolean loadRecursively, String parentFolderName) {
        logger.info("Loading files from folder: " + (parentFolderName != null ?
                                                        parentFolderName :
                                                        srcFolder.getName()));
        File[] files = srcFolder.listFiles();
        for (File file : files) {
            if (file.isFile()) {
                if (file.getName().toLowerCase().endsWith("jpg") || file.getName().toLowerCase().endsWith("jpeg")) {
                    try {
                        processingQueue.put(new JpegImageCompressionMessage(file, false));
                        logger.fine("File: " + file.getName() + " queued for processing!");
                        lock.lock();
                        this.messagesProduced += 1;
                        lock.unlock();
                    } catch (InterruptedException e) {
                        logger.severe(e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                if (loadRecursively) {
                    parentFolderName = parentFolderName == null ? file.getParentFile().getName() : parentFolderName;
                    StringBuilder parentFolder = new StringBuilder(parentFolderName)
                            .append(File.separator)
                            .append(file.getName());
                    loadJpegFiles(file, loadRecursively, parentFolder.toString());
                }
            }
        }
    }
}
