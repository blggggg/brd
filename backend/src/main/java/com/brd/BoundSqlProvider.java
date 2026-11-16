package com.brd;

import java.util.Map;

/** Converts application-owned positional SQL into MyBatis-bound parameters. */
public class BoundSqlProvider {
    public String statement(Map<String,Object> parameters) {
        String sql = String.valueOf(parameters.get("sql"));
        Object[] args = (Object[]) parameters.get("args");
        StringBuilder out = new StringBuilder(sql.length() + args.length * 12);
        char quote = 0;
        boolean escaped = false;
        int index = 0;
        for (int i=0; i<sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') { quote = c; out.append(c); continue; }
            if (c == '?') {
                if (index >= args.length) throw new IllegalArgumentException("SQL placeholder count exceeds argument count");
                out.append("#{args[").append(index++).append("]}");
            } else out.append(c);
        }
        if (index != args.length) throw new IllegalArgumentException("SQL placeholder count does not match argument count");
        return out.toString();
    }
}
