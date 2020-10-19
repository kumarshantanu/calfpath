//   Copyright (c) Shantanu Kumar. All rights reserved.
//   The use and distribution terms for this software are covered by the
//   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
//   which can be found in the file LICENSE at the root of this distribution.
//   By using this software in any fashion, you are agreeing to be bound by
//   the terms of this license.
//   You must not remove this notice, or any other, from this software.


package calfpath.route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal, URI matching utility class.
 *
 */
public class UriMatch {

    public static final int NO_URI_MATCH_INDEX = -2;

    public static boolean isPos(long n) {
        return n > 0;
    }

    // ----- match methods -----

    public static int dynamicUriMatch(String uri, int beginIndex, Map<Object, String> paramsMap, List<?> patternTokens,
            boolean attemptPartialMatch) {
        final int uriLength = uri.length();
        if (beginIndex >= uriLength) {  // may happen when previous partial-segment matched fully
            return NO_URI_MATCH_INDEX;
        }
        final Map<Object, String> pathParams = new HashMap<Object, String>();
        StringBuilder sb = null;
        int uriIndex = beginIndex;
        OUTER:
        for (final Object token: patternTokens) {
            if (uriIndex >= uriLength) {
                if (attemptPartialMatch) {
                    paramsMap.putAll(pathParams);
                    return /* full match */ uriLength;
                }
                return NO_URI_MATCH_INDEX;
            }
            if (token instanceof String) {
                final String tokenStr = (String) token;
                if (uri.startsWith(tokenStr, uriIndex)) {
                    uriIndex += tokenStr.length();
                    // at this point, uriIndex == uriLength if last string token
                } else {  // 'string token mismatch' implies no match
                    return NO_URI_MATCH_INDEX;
                }
            } else {
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.setLength(0);  // reset buffer before use
                for (int j = uriIndex; j < uriLength; j++) {
                    final char ch = uri.charAt(j);
                    if (ch == '/') {
                        pathParams.put(token, sb.toString());
                        uriIndex = j;
                        continue OUTER;
                    } else {
                        sb = sb.append(ch);
                    }
                }
                pathParams.put(token, sb.toString());
                uriIndex = uriLength;  // control reaching this point means URI template ending in ":param*"
            }
        }
        if (uriIndex < uriLength) {  // 'tokens finished but URI still in progress' implies partial or no match
            if (attemptPartialMatch) {
                paramsMap.putAll(pathParams);
                return /* full match */ uriIndex;
            }
            return NO_URI_MATCH_INDEX;
        }
        paramsMap.putAll(pathParams);
        return /* full match */ uriLength;
    }

    public static int dynamicUriPartialMatch(String uri, int beginIndex, Map<Object, String> paramsMap, List<?> patternTokens) {
        return dynamicUriMatch(uri, beginIndex, paramsMap, patternTokens, true);
    }

    public static int dynamicUriFullMatch(String uri, int beginIndex, Map<Object, String> paramsMap, List<?> patternTokens) {
        return dynamicUriMatch(uri, beginIndex, paramsMap, patternTokens, false);
    }

    public static int staticUriPartialMatch(String uri, int beginIndex, String token) {
        final int uriLength = uri.length();
        if (uri.startsWith(token, beginIndex)) {
            final int tokenLength = token.length();
            return (uriLength - beginIndex) == tokenLength?
                    /* full match */ uriLength: /* partial match */ (beginIndex + tokenLength);
        }
        return NO_URI_MATCH_INDEX;
    }

    public static int staticUriFullMatch(String uri, int beginIndex, String token) {
        final int uriLength = uri.length();
        if (beginIndex == 0) {
            return uri.equals(token)? /* full match */ uriLength: NO_URI_MATCH_INDEX;
        }
        return (uri.startsWith(token, beginIndex) && (uriLength - beginIndex == token.length()))?
                /* full match */ uriLength: NO_URI_MATCH_INDEX;
    }

}
