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
    public static Object[] matchURI(String uri, int beginIndex, List<?> patternTokens, boolean attemptPartialMatch) {
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
        final Map<Object, String> pathParams = new HashMap<Object, String>(tokenCount);
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

}
