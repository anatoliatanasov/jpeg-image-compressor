package name.atanasov.home.jpegcompressor.processing.compressing;

import name.atanasov.home.jpegcompressor.processing.IStageProcessor;
import name.atanasov.home.jpegcompressor.processing.ImageCompressionQueue;
import name.atanasov.home.jpegcompressor.processing.JpegImageCompressionMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Created by anatoli on 8/3/16.
 */
public class ImageCompressor implements IStageProcessor {
    private static final Logger logger = Logger.getLogger(ImageCompressor.class.getName());
    private ImageCompressionQueue processingQueue = null;
    private long totalSize = 0;
    private int messagesConsumed = 0;
    private int numberOfThreads = 1;

    private static final ReentrantLock lock = new ReentrantLock();

    public ImageCompressor(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
    }

    @Override
    public void setMessageQueue(ImageCompressionQueue queue) {
        this.processingQueue = queue;
    }

    private void processMessage(JpegImageCompressionMessage message) {
        logger.fine("Processing file: " + message.getJpegImageFile().getName());
        lock.lock();
        this.totalSize += message.getJpegImageFile().length();
        this.messagesConsumed += 1;
        lock.unlock();
    }

    @Override
    public void process() {
        lock.lock();
        this.messagesConsumed = 0;
        lock.unlock();

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        final long before = System.currentTimeMillis();
        while(true) {
            try {
                JpegImageCompressionMessage message = processingQueue.take();
                if(message.isInterruptingMessage()) {
                    break;
                }

                executor.submit(() -> {
                   processMessage(message);
                });
            } catch(InterruptedException ie) {
                ie.printStackTrace();
            }
        }

        try {
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            final long after = System.currentTimeMillis();
            logger.info("Total number of processed image: [" + this.messagesConsumed + "].");
            logger.info("Images processed for: [" + (after - before) + "] ms");
            logger.info("Total size of processed files: [" + (totalSize/1024/1024) + "] MB");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if(!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }
}
