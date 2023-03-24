package dev.efnilite.ip.util;

import org.apache.commons.lang.time.DurationFormatUtils;

/**
 * A stopwatch that... counts
 *
 * @author Efnilite
 */
public class Stopwatch {

    private long start;

    public boolean hasStarted() {
        return start != 0;
    }

    public void start() {
        this.start = System.currentTimeMillis();
    }

    public void stop() {
        start = 0;
    }

    @Override
    public String toString() {
        if (start == 0) {
            return "0.0s";
        }
        long delta = System.currentTimeMillis() - start;
        String format = DurationFormatUtils.formatDuration(delta, "HH:mm:ss:SSS", true);
        String[] split = format.split(":");
        String updated = "";
        int hours = Integer.parseInt(split[0]);
        if (hours > 0) {
            updated += hours + "h ";
        }
        int mins = Integer.parseInt(split[1]);
        if (mins > 0) {
            updated += mins + "m ";
        }
        int secs = Integer.parseInt(split[2]);
        updated += secs;
        int ms = Integer.parseInt(split[3]);
        String parsed = Integer.toString(ms);
        updated += "." + parsed.charAt(0) + "s";
        return updated;
    }
}
