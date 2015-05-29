package calfpath;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Util {

    private static final Map<?, String> EMPTY_PARAMS = Collections.emptyMap();

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
            if (i > n) {
                return null;
            }
            if (token instanceof String) {
                final String tokenStr = (String) token;
                if (uri.startsWith(tokenStr, i)) {
                    i += tokenStr.length();
                } else {
                    return null;
                }
            } else {
                final StringBuilder sb = new StringBuilder();
                for (int j = i; j < n; j++) {
                    final char ch = uri.charAt(j);
                    if (ch == '/') {
                        params.put(token, sb.toString());
                        i = j;
                        continue OUTER;
                    } else {
                        sb.append(ch);
                    }
                }
                params.put(token, sb.toString());
                i = Integer.MAX_VALUE;
            }
        }
        return params;
    }

}
