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

    @SuppressWarnings("unchecked")
    private static final Map<?, String> EMPTY_PARAMS = Collections.unmodifiableMap(Collections.EMPTY_MAP);

    public static Map<?, String> matchURI(String uri, List<?> patternTokens) {
        // if length==1, then token must be string
        if (patternTokens.size() == 1) {
            if (uri.equals(patternTokens.get(0))) {
                return EMPTY_PARAMS;
            } else {
                return null;
            }
        }
        final int n = uri.length();
        final Map<Object, String> params = new HashMap<Object, String>();
        int i = 0;
        OUTER:
        for (final Object token: patternTokens) {
            if (i >= n) {
                return null;
            }
            if (token instanceof String) {
                final String tokenStr = (String) token;
                if (uri.startsWith(tokenStr, i)) {
                    i += tokenStr.length();  // now i==n if last string token
                } else {  // 'string token mismatch' implies no match
                    return null;
                }
            } else {
                final StringBuilder sb = new StringBuilder();
                for (int j = i; j < n; j++) {  // capture param chars in one pass
                    final char ch = uri.charAt(j);
                    if (ch == '/') {  // separator implies we got param value, now continue
                        params.put(token, sb.toString());
                        i = j;
                        continue OUTER;
                    } else {
                        sb.append(ch);
                    }
                }
                // 'separator not found' implies URI has ended
                params.put(token, sb.toString());
                i = n;
            }
        }
        if (i < n) {  // 'tokens finished but URI still in progress' implies no match
            return null;
        }
        return params;
    }

}
