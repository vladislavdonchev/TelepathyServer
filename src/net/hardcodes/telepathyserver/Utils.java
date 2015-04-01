package net.hardcodes.telepathyserver;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

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

    private static long getTotalMem() {
        return Runtime.getRuntime().totalMemory();
    }

    public static String getResourcesInfo() {
        return "CPU: " + monitoringThread.getTotalUsage() + "% | MEM: " + Utils.getUsedMem() / (1024 * 1024) + "/" + Utils.getTotalMem() / (1024 * 1024) + " MB";
    }

    public static boolean areThereResourcesLeft() {
        return monitoringThread.getTotalUsage() < 90 && getUsedMem() / getTotalMem() < 1;
    }

    private static long getUsedMem() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public static void saveUserProfiles(ArrayList<User> userProfiles) {
        saveFile("users.json", new Gson().toJson(userProfiles));
    }

    public static ConcurrentHashMap<String, User> loadUserProfiles() {
        ConcurrentHashMap<String, User> userProfilesMap = new ConcurrentHashMap<String, User>();
        String userProfilesJson = readFile("users.json");
        if (userProfilesJson != null) {
            for (User userProfile : (ArrayList<User>) new Gson().fromJson(userProfilesJson, new TypeToken<ArrayList<User>>() {
            }.getType())) {
                userProfilesMap.put(userProfile.getUserName(), userProfile);
            }
        }
        return userProfilesMap;
    }

    private static void saveFile(String filePath, String contents) {
        Charset utf8 = StandardCharsets.UTF_8;
        try {
            if (Files.exists(Paths.get(filePath))) {
                Files.delete(Paths.get(filePath));
            }
            Files.write(Paths.get(filePath), contents.getBytes(utf8), StandardOpenOption.CREATE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readFile(String filePath) {
        byte[] contents = null;
        try {
            contents = Files.readAllBytes(Paths.get(filePath));
        } catch (IOException e) {
            return null;
        }
        if (contents != null) {
            try {
                return new String(contents, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    public static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer();

            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
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
