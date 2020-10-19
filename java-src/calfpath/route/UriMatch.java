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

    public static final int FULL_URI_MATCH_INDEX = -1;
    public static final int NO_URI_MATCH_INDEX = -2;
    public static final int LO_URI_MATCH_INDEX = -3;
    public static final int HI_URI_MATCH_INDEX = -4;

    private static int partialUriMatch(Map<Object, String> paramsMap, Map<Object, String> pathParams, int uriIndex) {
        paramsMap.putAll(pathParams);
        return uriIndex;
    }

    private static int fullUriMatch(Map<Object, String> paramsMap, Map<Object, String> pathParams) {
        paramsMap.putAll(pathParams);
        return FULL_URI_MATCH_INDEX;
    }

    // ----- match methods -----

    public static int dynamicUriMatch(String uri, int beginIndex, Map<Object, String> paramsMap, List<?> patternTokens,
            boolean attemptPartialMatch) {
        final int uriLength = uri.length();
        if (beginIndex >= uriLength) {  // may happen when previous partial-segment matched fully
            return NO_URI_MATCH_INDEX;
        }
        final Map<Object, String> pathParams = new HashMap<Object, String>();
        StringBuilder BUFFER = null;
        int uriIndex = beginIndex;
        OUTER:
        for (final Object token: patternTokens) {
            if (uriIndex >= uriLength) {
                return attemptPartialMatch? partialUriMatch(paramsMap, pathParams, uriLength): NO_URI_MATCH_INDEX;
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
                if (BUFFER == null) {
                    BUFFER = new StringBuilder();
                } else {
                    BUFFER.setLength(0);  // reset buffer before use
                }
                for (int j = uriIndex; j < uriLength; j++) {
                    final char ch = uri.charAt(j);
                    if (ch == '/') {
                        pathParams.put(token, BUFFER.toString());
                        uriIndex = j;
                        continue OUTER;
                    } else {
                        BUFFER.append(ch);
                    }
                }
                pathParams.put(token, BUFFER.toString());
                uriIndex = uriLength;  // control reaching this point means URI template ending in ":param*"
            }
        }
        if (uriIndex < uriLength) {  // 'tokens finished but URI still in progress' implies partial or no match
            return attemptPartialMatch? partialUriMatch(paramsMap, pathParams, uriIndex): NO_URI_MATCH_INDEX;
        }
        return fullUriMatch(paramsMap, pathParams);
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
                    FULL_URI_MATCH_INDEX: /* partial match */ (beginIndex + tokenLength);
        }
        return NO_URI_MATCH_INDEX;
    }

    public static int staticUriFullMatch(String uri, int beginIndex, String token) {
        final int uriLength = uri.length();
        if (beginIndex == 0) {
            return uri.equals(token)? FULL_URI_MATCH_INDEX: NO_URI_MATCH_INDEX;
        }
        return (uri.startsWith(token, beginIndex) && (uriLength - beginIndex == token.length()))?
                FULL_URI_MATCH_INDEX: NO_URI_MATCH_INDEX;
    }

}
