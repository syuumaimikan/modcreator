package modstudio.core;

import modstudio.core.Elements.Condition;
import modstudio.core.Elements.Step;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Text form of procedure steps ("Mod Studio Script"). It converts both ways with the step tree used by the
 * block and graph editors, so the same logic can be written as code or assembled visually.
 *
 * <pre>
 * # comment
 * message(text="Welcome!", color=gold)
 * give(@s, minecraft:diamond, 2)
 * if not sneaking {
 *     effect(effect=minecraft:speed, seconds=5)
 * } else if chance(25) {
 *     particle(minecraft:heart)
 * } else {
 *     stop()
 * }
 * repeat(3) {
 *     sound(minecraft:entity.experience_orb.pickup)
 * }
 * </pre>
 */
public final class Script {
    private Script() {}

    /** A syntax error with a 1-based line and column. */
    public static final class ParseException extends Exception {
        public final int line, column;

        ParseException(String message, int line, int column) {
            super(message);
            this.line = line;
            this.column = column;
        }
    }

    // ------------------------------------------------------------------ printing

    private static final Pattern BARE = Pattern.compile("[A-Za-z0-9_:.~^@\\-+/#*]+");

    public static String print(List<Step> steps) {
        StringBuilder sb = new StringBuilder();
        block(steps, sb, "");
        return sb.toString();
    }

    private static void block(List<Step> steps, StringBuilder sb, String indent) {
        for (Step s : steps) statement(s, sb, indent);
    }

    private static void statement(Step s, StringBuilder sb, String indent) {
        if (s.kind.equals("if")) {
            sb.append(indent);
            ifChain(s, sb, indent);
            sb.append('\n');
            return;
        }
        sb.append(indent).append(call(s.kind, Logic.STEPS.get(s.kind), s.p));
        if (Logic.hasBody(s.kind)) {
            sb.append(" {\n");
            block(s.then, sb, indent + "    ");
            sb.append(indent).append('}');
        }
        sb.append('\n');
    }

    private static void ifChain(Step s, StringBuilder sb, String indent) {
        Condition c = s.cond == null ? Logic.newCondition("chance") : s.cond;
        sb.append("if ");
        if (c.negate) sb.append("not ");
        Logic.Def d = Logic.CONDITIONS.get(c.kind);
        if (d != null && d.params().isEmpty()) sb.append(c.kind);
        else sb.append(call(c.kind, d, c.p));
        sb.append(" {\n");
        block(s.then, sb, indent + "    ");
        sb.append(indent).append('}');
        if (s.orElse.size() == 1 && s.orElse.get(0).kind.equals("if")) {
            sb.append(" else ");
            ifChain(s.orElse.get(0), sb, indent);
        } else if (!s.orElse.isEmpty()) {
            sb.append(" else {\n");
            block(s.orElse, sb, indent + "    ");
            sb.append(indent).append('}');
        }
    }

    /** name(a, key=value): leading parameters that keep their order are positional, the rest named. */
    private static String call(String name, Logic.Def d, Map<String, String> values) {
        StringBuilder sb = new StringBuilder(name).append('(');
        if (d != null) {
            List<String> parts = new ArrayList<>();
            boolean positional = true;
            for (Logic.Param p : d.params()) {
                String v = values.getOrDefault(p.key(), p.def());
                if (v.equals(p.def())) {
                    positional = false;
                    continue;
                }
                parts.add(positional ? value(v) : p.key() + "=" + value(v));
            }
            sb.append(String.join(", ", parts));
        }
        return sb.append(')').toString();
    }

    private static String value(String v) {
        if (BARE.matcher(v).matches()) return v;
        return '"' + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    // ------------------------------------------------------------------ parsing

    public static List<Step> parse(String text) throws ParseException {
        Parser p = new Parser(text);
        List<Step> r = p.statements(false);
        p.skipSpace();
        if (!p.eof()) throw p.error("Unexpected \"" + p.peek() + "\"");
        return r;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s.replace("\r\n", "\n");
        }

        boolean eof() {
            return i >= s.length();
        }

        char peek() {
            return eof() ? '\0' : s.charAt(i);
        }

        ParseException error(String msg) {
            int line = 1, col = 1;
            for (int k = 0; k < Math.min(i, s.length()); k++) {
                if (s.charAt(k) == '\n') {
                    line++;
                    col = 1;
                } else col++;
            }
            return new ParseException(msg, line, col);
        }

        /** Skips whitespace and comment lines ("#" or "//" where a statement can start). */
        void skipSpace() {
            while (!eof()) {
                char c = peek();
                if (Character.isWhitespace(c)) i++;
                else if (c == '#' || (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/')) {
                    while (!eof() && peek() != '\n') i++;
                } else break;
            }
        }

        void skipInline() {
            while (!eof() && (peek() == ' ' || peek() == '\t' || peek() == '\n' || peek() == '\r')) i++;
        }

        String ident() throws ParseException {
            int start = i;
            while (!eof() && (Character.isLetterOrDigit(peek()) || peek() == '_')) i++;
            if (start == i) throw error("Expected a name");
            return s.substring(start, i);
        }

        boolean accept(char c) {
            skipInline();
            if (peek() == c) {
                i++;
                return true;
            }
            return false;
        }

        void expect(char c) throws ParseException {
            if (!accept(c)) throw error("Expected \"" + c + "\"");
        }

        boolean keyword(String kw) {
            skipInline();
            if (s.startsWith(kw, i)) {
                int end = i + kw.length();
                if (end >= s.length() || !(Character.isLetterOrDigit(s.charAt(end)) || s.charAt(end) == '_')) {
                    i = end;
                    return true;
                }
            }
            return false;
        }

        List<Step> statements(boolean inBlock) throws ParseException {
            List<Step> r = new ArrayList<>();
            while (true) {
                skipSpace();
                if (eof()) {
                    if (inBlock) throw error("Missing \"}\"");
                    return r;
                }
                if (peek() == '}') {
                    if (!inBlock) throw error("Unexpected \"}\"");
                    return r;
                }
                r.add(statement());
            }
        }

        List<Step> body() throws ParseException {
            expect('{');
            List<Step> r = statements(true);
            expect('}');
            return r;
        }

        Step statement() throws ParseException {
            if (keyword("if")) return ifStatement();
            int at = i;
            String name = ident();
            Logic.Def d = Logic.STEPS.get(name);
            if (d == null || name.equals("if")) {
                i = at;
                throw error("Unknown step \"" + name + "\"");
            }
            Step st = Logic.newStep(name);
            args(d, st.p, name);
            if (Logic.hasBody(name)) {
                skipInline();
                if (peek() == '{') st.then.addAll(body());
            }
            return st;
        }

        Step ifStatement() throws ParseException {
            Step st = Logic.newStep("if");
            boolean negate = keyword("not");
            skipInline();
            int at = i;
            String name = ident();
            Logic.Def d = Logic.CONDITIONS.get(name);
            if (d == null) {
                i = at;
                throw error("Unknown condition \"" + name + "\"");
            }
            Condition c = Logic.newCondition(name);
            c.negate = negate;
            skipInline();
            if (peek() == '(') args(d, c.p, name);
            st.cond = c;
            st.then.addAll(body());
            int save = i;
            if (keyword("else")) {
                skipInline();
                if (keyword("if")) st.orElse.add(ifStatement());
                else st.orElse.addAll(body());
            } else i = save;
            return st;
        }

        void args(Logic.Def d, Map<String, String> into, String name) throws ParseException {
            expect('(');
            int pos = 0;
            boolean named = false;
            skipInline();
            if (accept(')')) return;
            while (true) {
                skipInline();
                int at = i;
                String key = null;
                // key=value ?
                int save = i;
                if (Character.isLetter(peek())) {
                    String id = ident();
                    skipInline();
                    if (peek() == '=' ) {
                        i++;
                        key = id;
                    } else i = save;
                }
                String v = value();
                if (key != null) {
                    named = true;
                    String k = key;
                    if (d.params().stream().noneMatch(p -> p.key().equals(k))) {
                        i = at;
                        throw error("\"" + name + "\" has no parameter \"" + key + "\"");
                    }
                    into.put(key, v);
                } else {
                    if (named) throw error("Positional values must come before named ones");
                    if (pos >= d.params().size()) throw error("Too many values for \"" + name + "\"");
                    into.put(d.params().get(pos++).key(), v);
                }
                if (accept(',')) continue;
                expect(')');
                return;
            }
        }

        String value() throws ParseException {
            skipInline();
            if (peek() == '"' || peek() == '\'') {
                char q = s.charAt(i++);
                StringBuilder sb = new StringBuilder();
                while (true) {
                    if (eof()) throw error("Unclosed text");
                    char c = s.charAt(i++);
                    if (c == q) break;
                    if (c == '\\' && !eof()) {
                        char n = s.charAt(i++);
                        sb.append(n == 'n' ? '\n' : n);
                    } else sb.append(c);
                }
                return sb.toString();
            }
            int start = i, depth = 0;
            while (!eof()) {
                char c = peek();
                if (c == '[' || c == '{') depth++;
                else if (c == ']' || c == '}') {
                    if (depth == 0) break;
                    depth--;
                } else if (depth == 0 && (c == ',' || c == ')' || c == '\n')) break;
                i++;
            }
            String v = s.substring(start, i).trim();
            if (v.isEmpty()) throw error("Expected a value");
            return v;
        }
    }
}
