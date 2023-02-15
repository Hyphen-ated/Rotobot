package hyphenated;

import org.apache.commons.io.IOUtils;

import java.io.FileReader;

public class RulesDAO {
    public static String getRules() {
        try {
            return IOUtils.toString(new FileReader("rules.txt"));
        } catch (Exception e) {
            return "Error trying to read rules file";
        }

    }
}
