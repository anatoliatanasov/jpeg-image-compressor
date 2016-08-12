package name.atanasov.home.jpegcompressor.processing.compressing;

import name.atanasov.home.jpegcompressor.processing.IStageProcessor;
import name.atanasov.home.jpegcompressor.processing.ImageCompressionQueue;
import name.atanasov.home.jpegcompressor.processing.JpegImageCompressionMessage;

import javax.imageio.IIOImage;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by anatoli on 8/3/16.
 */
public class ImageCompressor implements IStageProcessor {
    private static final Logger logger = Logger.getLogger(ImageCompressor.class.getName());
    private ImageCompressionQueue processingQueue = null;
    private long totalSize = 0;
    private int messagesConsumed = 0;
    private Integer numberOfThreads = 1;
    private Float compressionRatio = 0.90f;

    private static final ReentrantLock lock = new ReentrantLock();

    public ImageCompressor(Integer numberOfThreads, Float compressionRatio) {
        if(numberOfThreads != null) {
           this.numberOfThreads = numberOfThreads;
        }

        if(compressionRatio != null) {
            this.compressionRatio = compressionRatio;
        }
    }

    @Override
    public void setMessageQueue(ImageCompressionQueue queue) {
        this.processingQueue = queue;
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

    private void processMessage(JpegImageCompressionMessage message) {
        logger.fine("Processing file: " + message.getJpegImageFile().getName());

        ImageReader reader = null;
        ImageInputStream iis = null;
        BufferedImage srcImage = null;
        IIOMetadata srcImageMetadata = null;
        try {
            final long before = System.currentTimeMillis();
            iis = new FileImageInputStream(message.getJpegImageFile());

            reader = getJpegImageReader();

            reader.setInput(iis);

            srcImage = reader.read(0);
            srcImageMetadata = reader.getImageMetadata(0);
            final long after = System.currentTimeMillis();

            logger.fine("Successfully read JPEG image: " + message.getJpegImageFile().getName() +
                    " for [" + (after - before) + "] ms");
        } catch (Exception e) {
            logger.severe("Unable to read image: " + message.getJpegImageFile().getName() +
                    ". Caused by: " + e.getMessage());
            logger.info("Please, enable the file logging by providing -logfile as application " +
                        "CLI argument. Details are logged in the file");
            logger.log(Level.FINE, "", e);

            return;
        } finally {
            if(iis != null) {
                try {
                    iis.close();
                } catch (IOException e) {
                    //
                }
            }
            if(reader != null) {
                reader.dispose();
            }
        }

        ImageWriter writer = null;
        ImageOutputStream ios = null;
        File destinationImageFile = null;
        try {
            final long before = System.currentTimeMillis();
            final int dotPos = message.getJpegImageFile().getName().indexOf('.');
            final String originalFileName = message.getJpegImageFile().getName();
            final String destinationImageFileName = new StringBuilder()
                                                    .append(message.getJpegImageFile().getParentFile().getCanonicalPath())
                                                    .append(File.separator)
                                                    .append(originalFileName.substring(0, dotPos))
                                                    .append("_compressed")
                                                    .append(originalFileName.substring(dotPos))
                                                    .toString();
            destinationImageFile = new File(destinationImageFileName);
            ios = new FileImageOutputStream(destinationImageFile);
            writer = getJpegImageWriter();

            writer.setOutput(ios);
            JPEGImageWriteParam compressionParams = new JPEGImageWriteParam(Locale.getDefault());
            compressionParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            compressionParams.setCompressionQuality(this.compressionRatio);
            compressionParams.setOptimizeHuffmanTables(true);

            final int scaledImageWidth = Math.round(srcImage.getWidth() * this.compressionRatio);
            final int scaledImageHeight = Math.round(srcImage.getHeight() * this.compressionRatio);
            Image scaledImage = srcImage.getScaledInstance(scaledImageWidth, scaledImageHeight, Image.SCALE_SMOOTH);
            BufferedImage newImage = new BufferedImage(scaledImage.getWidth(null), scaledImage.getHeight(null),
                                            BufferedImage.TYPE_INT_RGB);
            newImage.getGraphics().drawImage(scaledImage, 0, 0, null);
            writer.write(srcImageMetadata, new IIOImage(newImage, null, srcImageMetadata), compressionParams);

            final long after = System.currentTimeMillis();
            logger.fine("Successfully compressed image: " + message.getJpegImageFile().getName() +
                        "for [" + (after - before) + "] ms");
        } catch (Exception e) {
            logger.severe("Unable to compress image: " + message.getJpegImageFile().getName() +
                    ". Caused by: " + e.getMessage());
            logger.info("Please, enable the file logging by providing -logfile as application " +
                    "CLI argument. Details are logged in the file");
            logger.log(Level.FINE, "", e);

            if(destinationImageFile != null) {
                destinationImageFile.delete();
            }
        } finally {
            if(ios != null) {
                try {
                    ios.flush();
                    ios.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(writer != null) {
                writer.dispose();
            }
        }

        lock.lock();
        this.totalSize += message.getJpegImageFile().length();
        this.messagesConsumed += 1;
        lock.unlock();
        logger.fine("Successfully comrpessed image: " + message.getJpegImageFile().getName());
    }

    private ImageReader getJpegImageReader() throws IOException {
        IIORegistry serviceRegistry = IIORegistry.getDefaultInstance();
        Iterator<ImageReaderSpi> imageReaders = serviceRegistry.getServiceProviders(ImageReaderSpi.class, f -> {
            if(f instanceof com.sun.imageio.plugins.jpeg.JPEGImageReaderSpi) {
                return true;
            }

            return false;
        }, false);

        if(imageReaders.hasNext()) {
            ImageReaderSpi imageReaderSpi = imageReaders.next();
            return imageReaderSpi.createReaderInstance();
        }
        return null;
    }

    private ImageWriter getJpegImageWriter() throws IOException {
        IIORegistry serviceRegistry = IIORegistry.getDefaultInstance();
        Iterator<ImageWriterSpi> imageWriters = serviceRegistry.getServiceProviders(ImageWriterSpi.class,
                filter -> {
                    if(filter instanceof com.sun.imageio.plugins.jpeg.JPEGImageWriterSpi) {
                        return true;
                    }
                    return false;
                }, false);

        if(imageWriters.hasNext()) {
            return imageWriters.next().createWriterInstance();
        }

        return null;
    }

}
