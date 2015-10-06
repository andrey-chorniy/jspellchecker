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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymce.spellchecker.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Andrey Chorniy
 * Date: 03.01.2010
 * This is a base class for implementing TinyMCE JSON based request/response
 * Abstract classes define SpellcheckMethod required to implement spellchecking functionality/lifecycle
 *  
 * TODO: refactor to single servlet which will use particular implementation based on delegating SpellcheckMethod to spellchecker implementation
 * TODO: Delegate abstract SpellcheckMethod to interface (named as SpellcheckerAdapter for example) to move them away from servlet code
 * TODO: Servlet will only do specific servlet functions: Reading JSONRequest, delegate spellchecking functionality to SpellcheckerAdapter and write JSON-response
 * TODO: SpellcheckerAdapter creation will be done in the servlet.init() method
 */

public abstract class TinyMCESpellCheckerServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private Logger logger = LoggerFactory.getLogger(getClass());

    private static final String MAX_SUGGESTIONS_COUNT_PARAM = "maxSuggestionsCount";
    private static final String PRELOADED_LANGUAGES_PARAM = "preloadedLanguages";

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String GET_METHOD_RESPONSE_ERROR = "This servlet expects a JSON encoded body, POSTed to this URL";

    private static final String DEFAULT_PRELOADED_LANGUAGES = "en-us";

    private int maxSuggestionsCount = 25;


    @Override
    public void init() throws ServletException {
        super.init();
        preLoadSpellcheckers();
        readMaxSuggestionsCount();
    }

    private void preLoadSpellcheckers() throws ServletException {
        String preloaded = getServletConfig().getInitParameter(PRELOADED_LANGUAGES_PARAM);
        if (preloaded == null || preloaded.trim().length() == 0) {
            preloaded = DEFAULT_PRELOADED_LANGUAGES;
        }

        String[] preloadedLanguages = preloaded.split(";");
        for (String preloadedLanguage : preloadedLanguages) {
            try {
                preLoadLanguageChecker(preloadedLanguage);
            } catch (SpellCheckException e) {
                //wrong servlet configuration
                throw new ServletException(e);
            }
        }
    }

    protected abstract void preLoadLanguageChecker(String preloadedLanguage) throws SpellCheckException;

    /**
     * This method look for the already created SpellChecker object in the cache, if it is not present in the cache then
     * it try to load it and put newly created object in the cache. SpellChecker loading is quite expensive operation
     * to do it for every spell-checking request, so in-memory-caching here is almost a "MUST to have"
     *
     * @param lang the language code like "en" or "en-us"
     * @return instance of SpellChecker for particular implementation
     * @throws SpellCheckException if method failed to load the SpellChecker for lang (it happens if there is no
     *                             dictionaries for that language was found in the classpath
     */
    protected abstract Object getChecker(String lang) throws SpellCheckException;


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
        try {
            releaseResources();
        } catch (Throwable ex) {
            logger.error("Failed to releaseResources()", ex);
        }
    }

    /**
     * This method will be called from {@link javax.servlet.Servlet#destroy()} to release resources
     */
    protected abstract void releaseResources();


    /**
     * GET method is not supported
     * @see HttpServlet#doGet(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse
     *      response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setResponeHeaders(response);
        PrintWriter pw = response.getWriter();
        pw.println(GET_METHOD_RESPONSE_ERROR);
    }

    /**
     * There are three types of requests are supported
     * h3. "checkWords"
     * Check the list of words passed as a second argument of the "params" array
     * First argument of the params array, define the "language"
     *
     * h4. Example request body (JSON)
     * <pre>
     * {
     *   "method" : "checkWords",
     *   "params" : [ "en-us", ["usa","cawboy", "apple"] ]
     * }
     * </pre>
     * h4. Example Response (JSON)
     * <pre>
     *     {"id":null,"result":["cawboy"],"error":null}
     * </pre>
     *
     * h3. "getWordSuggestions"
     * Get suggestion for single word, passed as the 2-nd element in the "params" array
     * h4. Example request body (JSON)
     * <pre>
     *{
     *  "method" : "getWordSuggestions",
     *  "params" : [ "en-us", "Guugli" ]
     *}
     * </pre>
     * h4. Example Response (JSON)
     * <pre>
     *     {"id":null,"result":["Google"],"error":null}
     * </pre>
     *
     * h3. "spellcheck"
     * This kind of request is used by TinyMCE 4.0
     * h4. Example request body (JSON)
     * <pre>
     *{
     *  "id" : "${request-id}",
     *  "method" : "spellcheck",
     *  "params" : { "lang": "en",
     *               "words": ["cawboy", "apple", "Guugli"]
     *             }
     *}
     * </pre>
     * h4. Example Response (JSON)
     * <pre>
     * {
     *    "id":"${request-id}",
     *    "result": {
     *      "cawboy" : ["cowboy"],
     *      "Guugli" : ["Google"]
     *    }
     * }
     * </pre>
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setResponeHeaders(response);
        try {

            //——————————————————————————————-
            //1. Retrieve values of the request parameters (method, text, lang).
            //——————————————————————————————-
            String methodName = request.getParameter("method");
            if (!methodName.equals(SpellcheckMethod.spellcheck.name())) {
                throw new SpellCheckException("Spellchecker method is not supported:" + methodName);
            }

            String text = request.getParameter("text");
            String lang = request.getParameter("lang");

            logger.debug("methodName={} lang={}", methodName, lang);

            //————————————————–
            //2. Construct a list of words for spell checking.
            //————————————————–
            List <String>inputWords = constructWords(text);

            JSONObject jsonOutput = new JSONObject();

            //——————————————————————————-
            //3. For each word, do a spell-check. If a word is misspelled, add suggestions.
            //——————————————————————————-
            List<String> misspelledWordsList = findMisspelledWords(inputWords.iterator(), lang);
            JSONObject misspelledWordJSON = new JSONObject();

            for(String misspelled : misspelledWordsList){
                JSONArray suggestionJSONArray = getWordSuggestions(misspelled, lang);
                misspelledWordJSON.put(misspelled, suggestionJSONArray);
            }

            //————————————————–
            //4. Construct JsonObject in the correct format.
            //————————————————–
            jsonOutput.put("words", misspelledWordJSON);

            logger.debug("Response {}", jsonOutput);

            PrintWriter pw = response.getWriter();
            pw.println(jsonOutput.toString());

        } catch (SpellCheckException se) {
            logger.warn("Failed to perform spellcheck operation", se);
            returnError(response, se.getMessage());
        } catch (Exception e) {
            logger.warn("Failed to perform spellcheck operation (generic error)", e);
            returnError(response, e.getMessage());
        }

        response.getWriter().flush();
    }

    /**
     * @param response response
     */
    private void setResponeHeaders(HttpServletResponse response) {
        response.setContentType("text/plain; charset=utf-8");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Cache-Control", "no-store, no-cache");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", System.currentTimeMillis());
    }


    /**
     * write error response as JSONObject to output
     * @param response response
     * @param message error message
     */
    private void returnError(HttpServletResponse response, String message) {
        response.setStatus(500);
        try {
            response.getWriter().print("{'id':null,'response':[],'error':'" + message + "'}");
        } catch (IOException e) {
            logger.info("Failed to send error response to client", e);
        }
    }

    private List <String>constructWords(String text) {
        List<String> words = new ArrayList<String>();

        if(StringUtils.isNotBlank(text)){
            Pattern pattern = Pattern.compile("\\w+");
            Matcher matcher = pattern.matcher(text.trim());
            while (matcher.find()) {
                String word = matcher.group();
                words.add(word);
            }
        }
        return words;
    }

    private JSONArray getWordSuggestions(String word, String lang) throws SpellCheckException {
        JSONArray suggestions = new JSONArray();
        if (word != null) {
            List<String> suggestionsList = findSuggestions(word, lang, maxSuggestionsCount);
            for (String suggestion : suggestionsList) {
                suggestions.put(suggestion);
            }
        }
        return suggestions;
    }
    
    protected abstract List<String> findMisspelledWords(Iterator<String> checkedWordsIterator,
                                                        String lang) throws SpellCheckException;


    protected abstract List<String> findSuggestions(String word, String lang,
                                                    int maxSuggestionsCount) throws SpellCheckException;


}
