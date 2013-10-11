package org.tinymce.spellchecker;

import java.util.ArrayList;
import java.util.List;

/**
 * SpellcheckerUtils.java
 * Created on 10 11, 2013 by Andrey Chorniy
 */
public class SpellcheckerUtils {

    public static List<String> resolveLanguages(String s) {
        List<String> result = new ArrayList<String>(3);
        StringBuilder langBuilder = new StringBuilder();
        for (String part : s.split("-")) {
            langBuilder.append(part);
            result.add(langBuilder.toString());
            langBuilder.append("-");
        }
        return result;
    }
}
