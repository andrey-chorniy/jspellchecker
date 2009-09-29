package org.tinymce.spellchecker;

/*
 Copyright (c) 2008
 Rich Irwin <rirwin@seacliffedu.com>, Andrey Chorniy <andrey.chorniy@gmail.com>

 Permission is hereby granted, free of charge, to any person
 obtaining a copy of this software and associated documentation
 files (the "Software"), to deal in the Software without
 restriction, including without limitation the rights to use,
 copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the
 Software is furnished to do so, subject to the following
 conditions:

 The above copyright notice and this permission notice shall be
 included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 OTHER DEALINGS IN THE SOFTWARE.
*/

/**
 * @author Rich Irwin <rirwin@seacliffedu.com>
 * @author: Andrey Chorniy <andrey.chorniy@gmail.com>
 * Date: 24.09.2008
 */

import com.swabunga.spell.engine.Configuration;
import com.swabunga.spell.engine.SpellDictionaryHashMap;
import com.swabunga.spell.event.SpellChecker;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JazzySpellCheckerServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static Logger logger = Logger.getLogger(JazzySpellCheckerServlet.class.getName());

    private static final String MAX_SUGGESTIONS_COUNT_PARAM = "maxSuggestionsCount";
    private static final String PRELOADED_LANGUAGES_PARAM = "preloadedLanguages";

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String GET_METHOD_RESPONSE_ERROR = "This servlet expects a JSON encoded body, POSTed to this URL";

    private static String DEFAULT_PRELOADED_LANGUAGES = "en-us;es";

    private enum methods {
        checkWords, getSuggestions
    }

    private int maxSuggestionsCount = 25;

    private Map<String, SpellChecker> spellcheckersCache = new Hashtable<String, SpellChecker>(2);

    @Override
    public void init() throws ServletException {
        super.init();
        preloadSpellcheckers();
        readMaxSuggestionsCount();
    }

    private void preloadSpellcheckers() throws ServletException {
        String preloaded = getServletConfig().getInitParameter(PRELOADED_LANGUAGES_PARAM);
        if (preloaded == null || preloaded.trim().length() == 0) {
            preloaded = DEFAULT_PRELOADED_LANGUAGES;
        }

        for (String preloadedLanguage : preloaded.split(";")) {
            try {
                getChecker(preloadedLanguage);
            } catch (SpellCheckException e) {
                //wrong servlet configuration
                throw new ServletException(e);
            }
        }
    }

    private void readMaxSuggestionsCount() throws ServletException {
        String suggestionsCountParam = getServletConfig().getInitParameter(MAX_SUGGESTIONS_COUNT_PARAM);
        if (suggestionsCountParam != null && suggestionsCountParam.trim().length() > 0) {
            try {
                maxSuggestionsCount = Integer.parseInt(suggestionsCountParam.trim());
            } catch (NumberFormatException ex) {
                //wrong servlet configuration, possibly a typo
                throw new ServletException(ex);
            }
        }
    }


    /**
     * @see javax.servlet.Servlet#destroy()
     */
    public void destroy() {
        super.destroy();
        //remove unused objects from memory
        spellcheckersCache.clear();
        spellcheckersCache = new Hashtable<String, SpellChecker>();
    }

    /**
     * GET method is not supported
     *
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
     *      response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setResponeHeaders(response);
        PrintWriter pw = response.getWriter();
        pw.println(GET_METHOD_RESPONSE_ERROR);
    }

    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
     *      response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setResponeHeaders(response);
        try {
            JSONObject jsonInput = readRequest(request);

            String methodName = jsonInput.optString("method");
            if (methodName == null || methodName.trim().equals("")) {
                throw new SpellCheckException("Wrong spellchecker-method-name:" + methodName);
            }

            JSONObject jsonOutput = new JSONObject("{'id':null,'result':[],'error':null}");
            switch (methods.valueOf(methodName.trim())) {
                case checkWords:
                    jsonOutput.put("result", checkWords(jsonInput.optJSONArray("params")));
                    break;
                case getSuggestions:
                    jsonOutput.put("result", getSuggestions(jsonInput.optJSONArray("params")));
                    break;
                default:
                    throw new SpellCheckException("Unimplemented spellchecker method {" + methodName + "}");
            }

            PrintWriter pw = response.getWriter();
            pw.println(jsonOutput.toString());

        } catch (SpellCheckException se) {
            logger.log(Level.WARNING, se.getMessage(), se);
            returnError(response, se.getMessage());
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            returnError(response, e.getMessage());
        }

        response.getWriter().flush();
    }

    private JSONObject readRequest(HttpServletRequest request) throws SpellCheckException {
        JSONObject jsonInput = null;
        try {
            jsonInput = new JSONObject(getRequestBody(request));
        } catch (JSONException e) {
            throw new SpellCheckException("Could not interpret JSON request body", e);
        } catch (IOException e) {
            throw new SpellCheckException("Error reading request body", e);
        }
        return jsonInput;
    }

    /**
     * @param response
     */
    private void setResponeHeaders(HttpServletResponse response) {
        response.setContentType("text/plain; charset=utf-8");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Cache-Control", "no-store, no-cache");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", System.currentTimeMillis());
    }


    /**
     * @param request
     * @return read the request content and return it as String object
     * @throws IOException
     */
    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line = request.getReader().readLine();
        while (null != line) {
            sb.append(line);
            line = request.getReader().readLine();
        }
        return sb.toString();
    }

    /**
     * @param response
     * @param message
     */
    private void returnError(HttpServletResponse response, String message) {
        response.setStatus(500);
        try {
            response.getWriter().print("{'id':null,'response':[],'error':'" + message + "'}");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JSONArray checkWords(JSONArray params) throws SpellCheckException {
        JSONArray misspelled = new JSONArray();
        if (params != null) {
            JSONArray words = params.optJSONArray(1);
            String lang = params.optString(0);
            lang = ("".equals(lang)) ? DEFAULT_LANGUAGE : lang;
            SpellChecker checker = getChecker(lang);
            for (int i = 0; i < words.length(); i++) {
                String word = words.optString(i);
                if (!word.equals("") && !checker.isCorrect(word) && !checker.isCorrect(word.toLowerCase())) {
                    misspelled.put(word);
                }
            }
        }
        return misspelled;
    }

    private JSONArray getSuggestions(JSONArray params) throws SpellCheckException {
        JSONArray suggestions = new JSONArray();
        if (params != null) {
            String lang = params.optString(0);
            lang = ("".equals(lang)) ? DEFAULT_LANGUAGE : lang;
            String word = params.optString(1);
            SpellChecker checker = getChecker(lang);
            ListIterator<?> suggestionsIt = checker.getSuggestions(word, maxSuggestionsCount).listIterator();
            while (suggestionsIt.hasNext()) {
                suggestions.put(suggestionsIt.next());
            }
        }
        return suggestions;
    }

    /**
     * This method look for the already created SpellChecker object in the cache, if it is not present in the cache then
     * it try to load it and put newly created object in the cache. SpellChecker loading is quite expensive operation
     * to do it for every spell-checking request, so in-memory-caching here is almost a "MUST to have"
     *
     * @param lang the language code like "en" or "en-us"
     * @return instance of jazzy SpellChecker
     * @throws SpellCheckException if method failed to load the SpellChecker for lang (it happens if there is no
     *                             dictionaries for that language was found in the classpath
     */
    private SpellChecker getChecker(String lang) throws SpellCheckException {

        SpellChecker cachedCheker = spellcheckersCache.get(lang);
        if (cachedCheker == null) {
            cachedCheker = loadSpellChecker(lang);
            spellcheckersCache.put(lang, cachedCheker);
        }
        return cachedCheker;
    }

    /**
     * Load the SpellChecker object form the file-system.
     * TODO: It possibly worth to rework it to load from class-path, since current implementation assumes that WAR is exploeded which is not always can be the case
     *
     * @param lang
     * @return loaded SpellChecker object
     * @throws SpellCheckException
     */
    private SpellChecker loadSpellChecker(final String lang) throws SpellCheckException {
        SpellChecker checker = new SpellChecker();
        List<File> dictionariesFiles = getDictionaryFiles(lang);

        addDictionaries(checker, dictionariesFiles);
        configureSpellChecker(checker);

        return checker;
    }

    /**
     * @param language
     * @return List of spellcheckers dictionary files in the webapplicaiton /WEB-INF/dictionary/${language} for the language
     * @throws SpellCheckException if there is no dictionaries for the specifed language
     */

    protected List<File> getDictionaryFiles(String language) throws SpellCheckException {
        String pathToDictionaries = getServletContext().getRealPath("/WEB-INF/dictionary");
        File dictionariesDir = new File(pathToDictionaries);
        List<File> langDictionaries = getDictionaryFiles(language, dictionariesDir, language);
        if (langDictionaries.size() == 0) {
            throw new SpellCheckException("There is no dictionaries for the language=" + language);
        }
        List<File> globalDictionaries = getDictionaryFiles(language, dictionariesDir, "global");

        List<File> dictionariesFiles = new ArrayList<File>();
        dictionariesFiles.addAll(langDictionaries);
        dictionariesFiles.addAll(globalDictionaries);
        return dictionariesFiles;
    }

    private void configureSpellChecker(SpellChecker checker) {
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNOREDIGITWORDS, false);
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNOREINTERNETADDRESSES, true);
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNOREMIXEDCASE, true);
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNOREUPPERCASE, true);
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNORESENTENCECAPITALIZATION, false);
    }

    private void addDictionaries(SpellChecker checker, List<File> langDictionaries) throws SpellCheckException {
        for (File dicitonaryFile : langDictionaries) {
            Reader dictionaryReader = null;
            try {
                dictionaryReader = new BufferedReader(new InputStreamReader(new FileInputStream(dicitonaryFile)));
                checker.addDictionary(new SpellDictionaryHashMap(dictionaryReader));
            } catch (Exception ex) {
                throw new SpellCheckException("Failed to open dictionary=" + dicitonaryFile.getName(), ex);
            } finally {
                if (dictionaryReader != null) {
                    try {
                        dictionaryReader.close();
                    } catch (IOException e) {
                        logger.warning("Failed to close dictionary file " + dicitonaryFile.getPath());
                    }
                }
            }
        }
    }


    private List<File> getDictionaryFiles(final String lang, File dictionariesDir,
                                          final String prefix) throws SpellCheckException {
        File languageDictionary = new File(dictionariesDir, lang);
        File[] languageDictionaries = languageDictionary.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                if (pathname.isFile()) {
                    return pathname.getName().startsWith(prefix);
                }
                return false;
            }
        });

        List<File> dicitonaries = new ArrayList<File>();
        if (languageDictionaries != null) {
            dicitonaries.addAll(Arrays.asList(languageDictionaries));
        }
        return dicitonaries;
    }


}