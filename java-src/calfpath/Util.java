//   Copyright (c) Shantanu Kumar. All rights reserved.
//   The use and distribution terms for this software are covered by the
//   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
//   which can be found in the file LICENSE at the root of this distribution.
//   By using this software in any fashion, you are agreeing to be bound by
//   the terms of this license.
//   You must not remove this notice, or any other, from this software.


package calfpath;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Util {

    public static final Object[] NO_URI_MATCH = null;

    @SuppressWarnings("unchecked")
    public static final Map<?, String> NO_PARAMS = Collections.EMPTY_MAP;

    public static final int FULL_URI_MATCH_INDEX = -1;

    public static final Object[] FULL_URI_MATCH_NO_PARAMS = new Object[] {NO_PARAMS, FULL_URI_MATCH_INDEX};

    public static Object[] partialURIMatch(Map<?, String> params, int endIndex) {
        return new Object[] {params, endIndex};
    }

    public static Object[] partialURIMatch(int endIndex) {
        return new Object[] {NO_PARAMS, endIndex};
    }

    public static Object[] fullURIMatch(Map<?, String> params) {
        return new Object[] {params, FULL_URI_MATCH_INDEX};
    }

    /**
     * Match a URI against URI-pattern tokens and return match result on successful match, {@code null} otherwise.
     * When argument {@code attemptPartialMatch} is {@code true}, both full and partial match are attempted without any
     * performance penalty. When argument {@code attemptPartialMatch} is {@code false}, only a full match is attempted.
     * @param uri                 the URI string to match
     * @param beginIndex          beginning index in the URI string to match
     * @param patternTokens       URI pattern tokens to match the URI against
     * @param attemptPartialMatch whether attempt partial match when full match is not possible
     * @return                    a match result on successful match, {@literal null} otherwise
     */
    public static Object[] matchURI(String uri, int beginIndex, List<?> patternTokens, boolean attemptPartialMatch,
            Map<Object, String> paramsMap) {
        final int tokenCount = patternTokens.size();
        final Object firstToken = patternTokens.get(0);
        if (beginIndex == FULL_URI_MATCH_INDEX) { // if already a full-match then no need to match further
            if (tokenCount == 1 && "".equals(firstToken)) return FULL_URI_MATCH_NO_PARAMS;
            return NO_URI_MATCH;
        }
        // if length==1 and token is string, then it's a static URI
        if (tokenCount == 1 && firstToken instanceof String) {
            final String staticPath = (String) firstToken;
            if (uri.startsWith(staticPath, beginIndex)) {  // URI begins with the path, so at least partial match exists
                if ((uri.length() - beginIndex) == staticPath.length()) {  // if full match exists, then return as such
                    return FULL_URI_MATCH_NO_PARAMS;
                }
                return attemptPartialMatch? partialURIMatch(beginIndex + staticPath.length()): NO_URI_MATCH;
            } else {
                return NO_URI_MATCH;
            }
        }
        final int uriLength = uri.length();
        final Map<Object, String> pathParams = (paramsMap == null || paramsMap.isEmpty())?
                new HashMap<Object, String>(tokenCount): paramsMap;
        int uriIndex = beginIndex;
        OUTER:
        for (final Object token: patternTokens) {
            if (uriIndex >= uriLength) {
                return attemptPartialMatch? partialURIMatch(pathParams, uriIndex): NO_URI_MATCH;
            }
            if (token instanceof String) {
                final String tokenStr = (String) token;
                if (uri.startsWith(tokenStr, uriIndex)) {
                    uriIndex += tokenStr.length();  // now i==n if last string token
                } else {  // 'string token mismatch' implies no match
                    return NO_URI_MATCH;
                }
            } else {
                final StringBuilder sb = new StringBuilder();
                for (int j = uriIndex; j < uriLength; j++) {  // capture param chars in one pass
                    final char ch = uri.charAt(j);
                    if (ch == '/') {  // separator implies we got param value, now continue
                        pathParams.put(token, sb.toString());
                        uriIndex = j;
                        continue OUTER;
                    } else {
                        sb.append(ch);
                    }
                }
                // 'separator not found' implies URI has ended
                pathParams.put(token, sb.toString());
                uriIndex = uriLength;
            }
        }
        if (uriIndex < uriLength) {  // 'tokens finished but URI still in progress' implies partial or no match
            return attemptPartialMatch? partialURIMatch(pathParams, uriIndex): NO_URI_MATCH;
        }
        return fullURIMatch(pathParams);
    }

    public static final int NO_URI_MATCH_INDEX = -2;

    /**
     * Match given URI against string token and return {@code beginIndex} as follows:
     * incremented : partial match
     * {@code -1}  : full match
     * {@code -2}  : no match
     * Attempt partial match when full match is not possible.
     * @param uri the URI string to match
     * @param beginIndex beginning index in the URI string to match
     * @param token URI token string to match against
     * @return incremented (partial match), -1 (full match) or -2 (no match)
     */
    public static int partialMatchURIString(String uri, int beginIndex, String token) {
        if (beginIndex == 0) {
            if (uri.startsWith(token)) {
                final int tokenLength = token.length();
                return (uri.length() == tokenLength)? FULL_URI_MATCH_INDEX: tokenLength;
            } else {
                return NO_URI_MATCH_INDEX;
            }
        }
        if (beginIndex > 0) {
            if (uri.startsWith(token, beginIndex)) {
                if ((uri.length() - beginIndex) == token.length()) {
                    return FULL_URI_MATCH_INDEX;     // full URI match
                }
                return beginIndex + token.length();  // partial URI match
            } else {
                return NO_URI_MATCH_INDEX;
            }
        }
        if (beginIndex == FULL_URI_MATCH_INDEX) {
            if ("".equals(token)) return FULL_URI_MATCH_INDEX;
            return NO_URI_MATCH_INDEX;
        }
        return NO_URI_MATCH_INDEX;                  // no URI match
    }

    /**
     * Match given URI against string token and return {@code beginIndex} as follows:
     * {@code -1}  : full match
     * {@code -2}  : no match
     * Do not attempt partial match - only attempt full match.
     * @param uri the URI string to match
     * @param beginIndex beginning index in the URI string to match
     * @param token URI token string to match against
     * @return -1 (full match) or -2 (no match)
     */
    public static int fullMatchURIString(String uri, int beginIndex, String token) {
        if (beginIndex == 0) {
            return uri.equals(token)? FULL_URI_MATCH_INDEX: NO_URI_MATCH_INDEX;
        }
        if (beginIndex > 0) {
            return (uri.startsWith(token, beginIndex) && (uri.length() - beginIndex) == token.length())?
                    FULL_URI_MATCH_INDEX: NO_URI_MATCH_INDEX;
        }
        if (beginIndex == FULL_URI_MATCH_INDEX) {
            return ("".equals(token))? FULL_URI_MATCH_INDEX: NO_URI_MATCH_INDEX;
        }
        return NO_URI_MATCH_INDEX;                  // no URI match
    }

    public static Object[] array(Object obj1, Object obj2) {
        return new Object[] {obj1, obj2};
    }

}
