package net.hardcodes.telepathyserver;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * Created by MnQko on 26.1.2015 г..
 */
public class Utils {

    private static MonitoringThread monitoringThread;

    public static void startSystemMonitor() {
        if (monitoringThread == null) {
            monitoringThread = new MonitoringThread(1000);
        }
    }

    public static void stopSystemMonitor() {
        monitoringThread.stopMonitor();
        monitoringThread = null;
    }

    private static long totalMem() {
        return Runtime.getRuntime().totalMemory();
    }

    public static String getResourcesInfo() {
        return "CPU: " + monitoringThread.getTotalUsage() + "% | MEM: " + Utils.usedMem() / (1024 * 1024) + "/" + Utils.totalMem() / (1024 * 1024) + " MB";
    }

    public static boolean areThereResourcesLeft() {
        return monitoringThread.getTotalUsage() < 90 && usedMem() / totalMem() < 1;
    }

    private static long usedMem() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    private static String escape(String orig) {
        StringBuilder buffer = new StringBuilder(orig.length());

        for (int i = 0; i < orig.length(); i++) {
            char c = orig.charAt(i);
            switch (c) {
                case '\b':
                    buffer.append("\\b");
                    break;
                case '\f':
                    buffer.append("\\f");
                    break;
                case '\n':
                    buffer.append("<br />");
                    break;
                case '\r':
                    // ignore
                    break;
                case '\t':
                    buffer.append("\\t");
                    break;
                case '\'':
                    buffer.append("\\'");
                    break;
                case '\"':
                    buffer.append("\\\"");
                    break;
                case '\\':
                    buffer.append("\\\\");
                    break;
                case '<':
                    buffer.append("&lt;");
                    break;
                case '>':
                    buffer.append("&gt;");
                    break;
                case '&':
                    buffer.append("&amp;");
                    break;
                default:
                    buffer.append(c);
            }
        }

        return buffer.toString();
    }
}
