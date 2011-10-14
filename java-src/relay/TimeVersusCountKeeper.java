package relay;

import java.util.Arrays;
import java.util.Formatter;

public class TimeVersusCountKeeper {
    
    private final long[] UNITS;
    private final long   UNIT_DURATION_MILLIS;
    private final String UNIT_DURATION_NAME;
    
    private volatile long till = System.currentTimeMillis();
    private final String TILL_LOCK = new String("LOCK");
    
    private String getDurationName(final long unitDurationMillis) {
        final int unitCount = UNITS.length;
        final String s;
        String unitName;
        if (unitCount <= 1) {
            s = "";
            unitName = "unit of duration " + UNIT_DURATION_MILLIS + "ms";
        } else {
            s = "s";
            unitName = "unit(s) of duration " + UNIT_DURATION_MILLIS + "ms each";
        }
        if (UNIT_DURATION_MILLIS == 1000L)                         { unitName = "sec"; }
        else if (UNIT_DURATION_MILLIS == 60 * 1000L)               { unitName = "min"; }
        else if (UNIT_DURATION_MILLIS == 60 * 60 * 1000L)          { unitName = "hr";  }
        else if (UNIT_DURATION_MILLIS == 24 * 60 * 60 * 1000L)     { unitName = "day"  + s; }
        else if (UNIT_DURATION_MILLIS == 7 * 24 * 60 * 60 * 1000L) { unitName = "week" + s; }
        return unitName;
    }
    
    private synchronized void update() {
        long diff = 0;
        synchronized (TILL_LOCK) {
            diff = System.currentTimeMillis() - till;
        }
        
        if (diff > UNIT_DURATION_MILLIS) {
            final long TRUNCATE_UNITS_COUNT = diff / UNIT_DURATION_MILLIS;
            synchronized (UNITS) {
                if (TRUNCATE_UNITS_COUNT >= UNITS.length) {
                    Arrays.fill(UNITS, 0L);
                } else {
                    System.arraycopy(UNITS, 0, UNITS, (int) TRUNCATE_UNITS_COUNT,
                            UNITS.length - (int) TRUNCATE_UNITS_COUNT);
                    for (int i = 0; i < TRUNCATE_UNITS_COUNT; i++) {
                        UNITS[i] = 0;
                    }
                }
            }
            synchronized (TILL_LOCK) {
                till += TRUNCATE_UNITS_COUNT * UNIT_DURATION_MILLIS;
                //till = System.currentTimeMillis();
            }
        }
    }
    
    public TimeVersusCountKeeper() {
        UNITS = new long[10];         // last 10
        UNIT_DURATION_MILLIS = 1000L; // seconds
        UNIT_DURATION_NAME = getDurationName(UNIT_DURATION_MILLIS);
    }
    
    public TimeVersusCountKeeper(final int maxUnitsCount,
            final long unitLengthMillis) {
        this.UNITS = new long[maxUnitsCount];
        this.UNIT_DURATION_MILLIS = unitLengthMillis;
        UNIT_DURATION_NAME = getDurationName(UNIT_DURATION_MILLIS);
    }
    
    public void incrementBy(final long count) {
        update();
        synchronized (UNITS) {
            UNITS[0] += count;
        }
    }
    
    public void setCount(final long count) {
        update();
        synchronized (UNITS) {
            UNITS[0] = count;
        }
    }
    
    public long[] getElements() {
        update();
        synchronized (UNITS) {
            return UNITS.clone();
        }
    }
    
    @Override
    public String toString() {
        int unitsCount = 0;
        synchronized (UNITS) {
            unitsCount = UNITS.length;
        }
        return new StringBuilder()
        .append(unitsCount)
        .append(' ')
        .append(UNIT_DURATION_NAME)
        .append(": ")
        .append(getElementsAsString(1) /*Arrays.toString(units)*/)
        .toString();
    }
    
    public String getElementsAsString(final int minElementWidth) {
        final StringBuilder sb = new StringBuilder();
        final Formatter numFormatter = new Formatter(sb);
        final String format = "%" + minElementWidth + "d";
        final long[] array = getElements();
        long totalSum = 0;
        String delim = "";
        sb.append('[');
        for (int i = 0; array != null && i < array.length; i++) {
            numFormatter.format(delim + format, array[i]);
            totalSum += array[i];
            delim = ", ";
        }
        sb.append("](Avg:");
        numFormatter.format(format, (long) (totalSum / array.length));
        sb.append(')');
        return sb.toString();
    }
    
    public static void main(String[] args) {
        final TimeVersusCountKeeper keeper = new TimeVersusCountKeeper();
        for (int i = 0; i < 100; i++) {
            TimerUtil.sleep(500);
            keeper.incrementBy(i);
            for (int j = 0; j < 100; j++) {
                keeper.incrementBy(1);
            }
            System.out.println(keeper.toString());
        }
    }
    
}