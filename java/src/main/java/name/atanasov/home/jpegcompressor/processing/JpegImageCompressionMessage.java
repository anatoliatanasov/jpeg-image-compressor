package name.atanasov.home.jpegcompressor.processing;

import java.io.File;

/**
 * Created by anatoli on 8/3/16.
 */
public class JpegImageCompressionMessage {
    private File jpegImageFile = null;
    private boolean interruptingMessage = false;

    public JpegImageCompressionMessage(File jpegImageFile, boolean interruptingMessage) {
        this.jpegImageFile = jpegImageFile;
        this.interruptingMessage = interruptingMessage;
    }

    public File getJpegImageFile() {
        return this.jpegImageFile;
    }

    public boolean isInterruptingMessage() {
        return this.interruptingMessage;
    }
 }
