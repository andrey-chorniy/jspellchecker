package org.tinymce.spellchecker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * ResourceProvider.java
 * Created on 5/13/13 by Andrey Chorniy
 */
public class ResourceProvider {
    private static ResourceProvider INSTANCE;

    private File dictionariesRootFolder;

    private ResourceProvider (){
        InputStream config = getClassPathResourceInputStream("/jspellchecker.properties",ResourceProvider.class);
        if (config == null) {
            throw new IllegalStateException("There is no jspellchecker.properties in the classpath");
        }
        try {
            Properties properties = new Properties();
            try {
                properties.load(config);
                String dictionariesDirectory = (String) properties.get("dictionaries.directory");
                if (dictionariesDirectory == null) {
                    throw new IllegalStateException("There is no dictionaries.directory property in the 'jspellchecker.properties'");
                }
                dictionariesRootFolder = new File(dictionariesDirectory);
                if (!dictionariesRootFolder.exists() || !dictionariesRootFolder.isDirectory()) {
                    throw new IllegalStateException("There is no such directory "+dictionariesDirectory);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load properties from jspellchecker.properties");
            }
        } finally {
            closeQuietly(config);
        }
    }


    public static ResourceProvider getInstance() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (ResourceProvider.class) {
            INSTANCE = new ResourceProvider();
        }
        return INSTANCE;
    }

    private static void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ignore) {
        }
    }





    public static File getClassPathResourceFile(String resourceName, Class accessor) {
        if (resourceName.startsWith("/")) {
            return new File(accessor.getResource(resourceName).getFile());
        }
        return getClassFolderResourceFile(resourceName, accessor);
    }

    public static InputStream getClassPathResourceInputStream(String resourceName, Class accessor) {
        if (resourceName.startsWith("/")) {
            return accessor.getResourceAsStream(resourceName);
        }
        return accessor.getResourceAsStream(resolveResourcePath(resourceName, accessor));
    }



    private static File getClassFolderResourceFile(String resourceName, Class clazz) {
        final String resolvedResourcePath = resolveResourcePath(resourceName, clazz);
        return new File(clazz.getResource(resolvedResourcePath).getFile());
    }

    private static String resolveResourcePath(String resourceName, Class clazz) {
        return new StringBuilder().append("/")
                    .append(clazz.getName().replace(".", "/")).append("/")
                    .append(resourceName).toString();
    }

    public File getDictionaryFile(String ... pathComponents) {
        return new File(dictionariesRootFolder, join(File.separator, pathComponents));
    }

    public static String join (String joiner, String ... components) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < components.length; i++){
            if (i > 0) {
                result.append(joiner);
            }
            result.append(components[i]);
        }
        return result.toString();
    }
}
