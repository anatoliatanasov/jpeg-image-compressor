package name.atanasov.home.jpegcompressor.processing;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by anatoli on 8/9/16.
 */
public class ImageCompressionQueue {
    private static ImageCompressionQueue ourInstance = new ImageCompressionQueue();
    private BlockingQueue<JpegImageCompressionMessage> internalQueue = null;

    public static ImageCompressionQueue getInstance() {
        return ourInstance;
    }

    private ImageCompressionQueue() {
        internalQueue = new LinkedBlockingQueue<JpegImageCompressionMessage>();
    }

    public void put(JpegImageCompressionMessage message) throws InterruptedException {
        internalQueue.put(message);
    }

    public JpegImageCompressionMessage take() throws  InterruptedException {
        return internalQueue.take();
    }
}
