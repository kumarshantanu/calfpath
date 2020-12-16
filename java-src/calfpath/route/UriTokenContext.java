//   Copyright (c) Shantanu Kumar. All rights reserved.
//   The use and distribution terms for this software are covered by the
//   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
//   which can be found in the file LICENSE at the root of this distribution.
//   By using this software in any fashion, you are agreeing to be bound by
//   the terms of this license.
//   You must not remove this notice, or any other, from this software.


package calfpath.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal, URI tokens based URI matching utility class. Instance of this class may be stored as a context object in
 * a Ring request. This class has unsynchronized mutable fields, accessed in a single thread.
 *
 */
public class UriTokenContext {

    public List<String> uriTokens;
    public int uriTokenCount;
    public Map<Object, String> paramsMap;

    public UriTokenContext(List<String> uriTokens, Map<Object, String> paramsMap) {
        this.uriTokens = uriTokens;
        this.uriTokenCount = uriTokens.size();
        this.paramsMap = paramsMap;
    }

    // ----- static utility -----

    public static List<String> parseUriTokens(String uri) {
//      if (uri == null || uri.isBlank()) {
//          throw new IllegalArgumentException("URI cannot be empty or blank");
//      }
//      char first = uri.charAt(0);
//      if (first != '/') {
//          throw new IllegalArgumentException("URI must begin with '/'");
//      }
        final int len = uri.length();
        final List<String> tokens = new ArrayList<String>();
        final StringBuilder sb = new StringBuilder();
        for (int i = 1; // start with second character, because first character is '/'
                i < len; i++) {
            final char ch = uri.charAt(i);
            if (ch == '/') {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    // ----- match constants -----

    public static final List<String> NO_MATCH_TOKENS = null;

    @SuppressWarnings("unchecked")
    public static final List<String> FULL_MATCH_TOKENS = Collections.EMPTY_LIST;

    private List<String> partialMatch(List<String> resultTokens) {
        this.uriTokens = resultTokens;
        this.uriTokenCount = resultTokens.size();
        return resultTokens;
    }

    private List<String> partialMatch(List<String> resultTokens, Map<Object, String> pathParams) {
        this.uriTokens = resultTokens;
        this.uriTokenCount = resultTokens.size();
        this.paramsMap.putAll(pathParams);
        return resultTokens;
    }

    private List<String> fullMatch(Map<Object, String> pathParams) {
        this.paramsMap.putAll(pathParams);
        this.uriTokens = FULL_MATCH_TOKENS;
        this.uriTokenCount = 0;
        return FULL_MATCH_TOKENS;
    }

    private List<String> fullMatch() {
        this.uriTokens = FULL_MATCH_TOKENS;
        this.uriTokenCount = 0;
        return FULL_MATCH_TOKENS;
    }

    // ----- match methods -----

    /**
     * (Partial) Match given URI tokens against URI pattern tokens. Meant for dynamic URI routes with path params.
     * Return a sub-list of URI tokens yet to be matched, to be interpreted as follows:
     * 
     * | Condition | Meaning       |
     * |-----------|---------------|
     * | `null`    | no match      |
     * | empty     | full match    |
     * | non-empty | partial match |
     * 
     * @param patternTokens
     * @return
     */
    public List<String> dynamicUriPartialMatch(List<?> patternTokens) {
        final int patternTokenCount = patternTokens.size();
        if ((uriTokenCount < patternTokenCount)) {
            return NO_MATCH_TOKENS;
        }
        final Map<Object, String> pathParams = new HashMap<Object, String>(patternTokenCount);
        for (int i = 0; i < patternTokenCount; i++) {
            final Object eachPatternToken = patternTokens.get(i);
            final String eachURIToken = uriTokens.get(i);
            if (eachPatternToken instanceof String) {
                if (!eachURIToken.equals(eachPatternToken)) {
                    return NO_MATCH_TOKENS;
                }
            } else {
                pathParams.put(eachPatternToken, eachURIToken);
            }
        }
        if (uriTokenCount > patternTokenCount) {
            return partialMatch(uriTokens.subList(patternTokenCount, uriTokenCount), pathParams); // partial match
        } else {
            return fullMatch(pathParams); // full match
        }
    }

    /**
     * (Full) Match given URI tokens against URI pattern tokens. Meant for dynamic URI routes with path params.
     * Return a sub-list of URI tokens yet to be matched, to be interpreted as follows:
     * 
     * | Condition | Meaning       |
     * |-----------|---------------|
     * | `null`    | no match      |
     * | empty     | full match    |
     * 
     * @param patternTokens
     * @return
     */
    public List<String> dynamicUriFullMatch(List<?> patternTokens) {
        final int patternTokenCount = patternTokens.size();
        if (uriTokenCount != patternTokenCount) {
            return NO_MATCH_TOKENS;
        }
        final Map<Object, String> pathParams = new HashMap<Object, String>(patternTokenCount);
        for (int i = 0; i < patternTokenCount; i++) {
            final Object eachPatternToken = patternTokens.get(i);
            final String eachURIToken = uriTokens.get(i);
            if (eachPatternToken instanceof String) {
                if (!eachURIToken.equals(eachPatternToken)) {
                    return NO_MATCH_TOKENS;
                }
            } else {
                pathParams.put(eachPatternToken, eachURIToken);
            }
        }
        paramsMap.putAll(pathParams); // mutate pathParams only on a successful match
        return FULL_MATCH_TOKENS; // full match
    }

    /**
     * (Partial) Match given URI tokens against URI pattern string tokens. Meant for static URI routes. Return the
     * tokens yet to be matched, interpreted as follows:
     * 
     * | Condition | Meaning       |
     * |-----------|---------------|
     * | `null`    | no match      |
     * | empty     | full match    |
     * | non-empty | partial match |
     * 
     * @param patternTokens
     * @return
     */
    public List<String> staticUriPartialMatch(List<String> patternTokens) {
        final int patternTokenCount = patternTokens.size();
        if (uriTokenCount < patternTokenCount) {
            return NO_MATCH_TOKENS;
        }
        for (int i = 0; i < patternTokenCount; i++) {
            if (!patternTokens.get(i).equals(uriTokens.get(i))) {
                return NO_MATCH_TOKENS;
            }
        }
        if (uriTokenCount > patternTokenCount) {
            return partialMatch(uriTokens.subList(patternTokenCount, uriTokenCount)); // partial match
        } else {
            return fullMatch(); // full match
        }
    }

    /**
     * (Full) Match given URI tokens against URI pattern string tokens. Meant for static URI routes. Return the
     * tokens yet to be matched, interpreted as follows:
     * 
     * | Condition | Meaning       |
     * |-----------|---------------|
     * | `null`    | no match      |
     * | empty     | full match    |
     * 
     * @param patternTokens
     * @return
     */
    public List<String> staticUriFullMatch(List<String> patternTokens) {
        final int patternTokenCount = patternTokens.size();
        if (uriTokenCount != patternTokenCount) {
            return NO_MATCH_TOKENS;
        }
        for (int i = 0; i < patternTokenCount; i++) {
            if (!patternTokens.get(i).equals(uriTokens.get(i))) {
                return NO_MATCH_TOKENS;
            }
        }
        return FULL_MATCH_TOKENS; // full match
    }

}
