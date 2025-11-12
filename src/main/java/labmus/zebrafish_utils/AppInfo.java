package labmus.zebrafish_utils;

import java.io.InputStream;
import java.util.Properties;

public class AppInfo {

    private static final Properties props = new Properties();

    static {
        try (InputStream is = AppInfo.class.getClassLoader()
                .getResourceAsStream("app.properties")) {
            if (is == null) {
                throw new RuntimeException("Could not find app.properties file.");
            }
            props.load(is);
        } catch (Exception ignored) {
        }
    }

    public static String getProperty(String propName){
        String propValue = props.getProperty(propName);
        return propValue == null ? "unknown" : propValue;
    }

}