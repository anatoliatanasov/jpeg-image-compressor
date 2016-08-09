package name.atanasov.home.jpegcompressor.processing;

/**
 * Created by anatoli on 8/4/16.
 */
public interface IStageProcessor {
    public void process();
    public void setMessageQueue(ImageCompressionQueue queue);
}
