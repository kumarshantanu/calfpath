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
 * Internal, URI index based URI matching utility class. Instance of this class may be stored as a context object in
 * a Ring request. This class has unsynchronized mutable fields, accessed in a single thread.
 *
 */
public class UriIndexContext {

    final StringBuilder BUFFER = new StringBuilder();

    public final String uri;
    public final int uriLength;
    public /***/ int uriBeginIndex;
    public final Map<Object, String> paramsMap;

    public void setUriBeginIndex(int uriIndex) {
        this.uriBeginIndex = uriIndex;
    }

    public UriIndexContext(String uri, Map<Object, String> paramsMap) {
        this.uri = uri;
        this.uriLength = uri.length();
        this.uriBeginIndex = 0;
        this.paramsMap = paramsMap;
    }

    public UriIndexContext(String uri, int uriLength, int uriBeginIndex, Map<Object, String> paramsMap) {
        this.uri = uri;
        this.uriLength = uriLength;
        this.uriBeginIndex = uriBeginIndex;
        this.paramsMap = paramsMap;
    }

    // ----- match constants -----

    public static final int FULL_URI_MATCH_INDEX = -1;
    public static final int NO_URI_MATCH_INDEX = -2;

    private int partialUriMatch(Map<Object, String> pathParams, int uriIndex) {
        this.paramsMap.putAll(pathParams);
        this.uriBeginIndex = uriIndex;
        return uriIndex;
    }

    private int partialUriMatch(int uriIndex) {
        this.uriBeginIndex = uriIndex;
        return uriIndex;
    }

    private int fullUriMatch() {
        this.uriBeginIndex = uriLength;
        return FULL_URI_MATCH_INDEX;
    }

    private int fullUriMatch(Map<Object, String> pathParams) {
        this.paramsMap.putAll(pathParams);
        this.uriBeginIndex = uriLength;
        return FULL_URI_MATCH_INDEX;
    }

    // ----- match methods -----

    public int dynamicUriMatch(List<?> patternTokens, boolean attemptPartialMatch) {
        if (uriBeginIndex >= uriLength) {
            return NO_URI_MATCH_INDEX;
        }
        final Map<Object, String> pathParams = new HashMap<Object, String>();
        int uriIndex = uriBeginIndex;
        OUTER:
        for (final Object token: patternTokens) {
            if (uriIndex >= uriLength) {
                return attemptPartialMatch? partialUriMatch(pathParams, uriLength): NO_URI_MATCH_INDEX;
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
                BUFFER.setLength(0);  // reset buffer before use
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
            return attemptPartialMatch? partialUriMatch(pathParams, uriIndex): NO_URI_MATCH_INDEX;
        }
        return fullUriMatch(pathParams);
    }

    public int dynamicUriPartialMatch(List<?> patternTokens) {
        return dynamicUriMatch(patternTokens, true);
    }

    public int dynamicUriFullMatch(List<?> patternTokens) {
        return dynamicUriMatch(patternTokens, false);
    }

    public int staticUriPartialMatch(String token) {
        if (uri.startsWith(token, uriBeginIndex)) {
            final int tokenLength = token.length();
            return (uriLength - uriBeginIndex) == tokenLength?
                    fullUriMatch(): partialUriMatch(uriBeginIndex + tokenLength);
        }
        return NO_URI_MATCH_INDEX;
    }

    public int staticUriFullMatch(String token) {
        if (uriBeginIndex == 0) {
            return uri.equals(token)? FULL_URI_MATCH_INDEX: NO_URI_MATCH_INDEX;
        }
        return (uri.startsWith(token, uriBeginIndex) && (uriLength - uriBeginIndex == token.length()))?
                FULL_URI_MATCH_INDEX: NO_URI_MATCH_INDEX;
    }

}
