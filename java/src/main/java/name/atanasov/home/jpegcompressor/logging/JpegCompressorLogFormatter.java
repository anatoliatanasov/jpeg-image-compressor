package name.atanasov.home.jpegcompressor.logging;

import java.util.Date;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Created by anatoli on 8/3/16.
 */
public class JpegCompressorLogFormatter extends SimpleFormatter {
    public JpegCompressorLogFormatter() {
        super();
    }

    @Override
    public synchronized String format(LogRecord record) {
        Object[] arguments = new Object[7];
        arguments[0] = new Date(record.getMillis());
        arguments[1] = record.getSourceClassName();
        arguments[2] = record.getLevel();
        arguments[3] = record.getLoggerName();

        arguments[4] = record.getMessage() != null ? record.getMessage() : "";
        arguments[5] = record.getThrown() != null ? record.getThrown() : "";
        arguments[6] = Thread.currentThread().getName();

        return String.format(MESSAGE_FORMAT,arguments);
    }

    private static final String MESSAGE_FORMAT = "[%3$-6s] [%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL] [%2$s] [%7$s] %5$s%6$s%n";
}
