package name.atanasov.home.jpegcompressor;

import java.util.Date;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    public static void main(String[] args) {
        Starter starter = Starter.getInstance();

        //prepares and configures the environment
        starter.prepareEnvironment(args);

        logger.info("JPEG compressor application launched on: " + new Date());

        //runs the application
        starter.startApplication();

        logger.info("JPEG compressor application finished on: " + new Date());
    }
}
