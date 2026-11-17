package modstudio.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for command branch paths such as {@code give <target:player> <amount:int(1,64)> [note:text]}.
 * Literals are plain words, required arguments use angle brackets, optional trailing arguments square brackets.
 */
public final class CommandSpec {
    private CommandSpec() {}

    public static final List<String> TYPES = List.of("word", "string", "text", "int", "double", "bool", "player", "players",
            "entity", "entities", "blockpos", "item", "block", "choice");

    public record Token(String name, String type, boolean literal, boolean optional, Double min, Double max, List<String> choices) {
        public boolean numeric() {
            return type.equals("int") || type.equals("double");
        }

        /** Text shown in usage strings and tree previews. */
        public String display() {
            if (literal) return name;
            String inner = name + (type.equals("word") ? "" : ":" + type);
            return optional ? "[" + inner + "]" : "<" + inner + ">";
        }
    }

    public static final class SpecException extends Exception {
        public SpecException(String message) {
            super(message);
        }
    }

    public static List<Token> parse(String path) throws SpecException {
        List<Token> out = new ArrayList<>();
        String s = path == null ? "" : path.trim();
        int i = 0;
        boolean optionalSeen = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '<' || c == '[') {
                char close = c == '<' ? '>' : ']';
                int end = findClose(s, i + 1, close);
                if (end < 0) throw new SpecException("missing " + close);
                Token t = argument(s.substring(i + 1, end).trim(), c == '[');
                if (!t.optional() && optionalSeen) throw new SpecException("required argument after optional: " + t.name());
                optionalSeen |= t.optional();
                out.add(t);
                i = end + 1;
            } else {
                int end = i;
                while (end < s.length() && !Character.isWhitespace(s.charAt(end))) end++;
                String word = s.substring(i, end);
                if (!word.matches("[a-z0-9_.-]+")) throw new SpecException("invalid literal: " + word);
                if (optionalSeen) throw new SpecException("literal after optional argument: " + word);
                out.add(new Token(word, "literal", true, false, null, null, List.of()));
                i = end;
            }
        }
        Map<String, Boolean> names = new LinkedHashMap<>();
        for (int k = 0; k < out.size(); k++) {
            Token t = out.get(k);
            if (t.literal()) continue;
            if (names.put(t.name(), true) != null) throw new SpecException("duplicate argument: " + t.name());
            if (t.type().equals("text") && k != out.size() - 1) throw new SpecException("text must be the last argument: " + t.name());
        }
        return out;
    }

    private static int findClose(String s, int from, char close) {
        int depth = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == close && depth <= 0) return i;
        }
        return -1;
    }

    private static Token argument(String body, boolean optional) throws SpecException {
        String name = body, type = "word", extra = null;
        int colon = body.indexOf(':');
        if (colon >= 0) {
            name = body.substring(0, colon).trim();
            type = body.substring(colon + 1).trim();
        }
        int paren = type.indexOf('(');
        if (paren >= 0) {
            if (!type.endsWith(")")) throw new SpecException("missing ) in " + body);
            extra = type.substring(paren + 1, type.length() - 1).trim();
            type = type.substring(0, paren).trim();
        }
        if (!name.matches("[a-z][a-z0-9_]*")) throw new SpecException("invalid argument name: " + name);
        if (!TYPES.contains(type)) throw new SpecException("unknown type: " + type);
        Double min = null, max = null;
        List<String> choices = new ArrayList<>();
        if (extra != null && !extra.isEmpty()) {
            if (type.equals("choice")) {
                for (String c : extra.split("[|,]")) {
                    String v = c.trim();
                    if (!v.matches("[A-Za-z0-9_.-]+")) throw new SpecException("invalid choice: " + v);
                    choices.add(v);
                }
            } else if (type.equals("int") || type.equals("double")) {
                String[] parts = extra.split(",", -1);
                try {
                    if (parts.length > 0 && !parts[0].isBlank()) min = Double.parseDouble(parts[0].trim());
                    if (parts.length > 1 && !parts[1].isBlank()) max = Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException e) {
                    throw new SpecException("invalid range: " + extra);
                }
                if (min != null && max != null && min > max) throw new SpecException("min above max: " + extra);
            } else {
                throw new SpecException("type " + type + " takes no options");
            }
        }
        if (type.equals("choice") && choices.isEmpty()) throw new SpecException("choice needs values, for example choice(red|blue)");
        return new Token(name, type, false, optional, min, max, List.copyOf(choices));
    }

    /** Parses without throwing; returns null when invalid. */
    public static List<Token> tryParse(String path) {
        try {
            return parse(path);
        } catch (SpecException e) {
            return null;
        }
    }

    /** Readable name for the function a branch compiles to. */
    public static String slug(List<Token> tokens, int index) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (!t.literal()) continue;
            if (!sb.isEmpty()) sb.append('_');
            sb.append(t.name().replaceAll("[^a-z0-9_]", "_"));
        }
        return sb.isEmpty() ? "branch" + index : sb.toString();
    }

    /** Variants of a path with trailing optional arguments dropped (longest first). */
    public static List<List<Token>> expansions(List<Token> tokens) {
        List<List<Token>> r = new ArrayList<>();
        r.add(tokens);
        for (int n = tokens.size() - 1; n >= 0; n--) {
            if (!tokens.get(n).optional()) break;
            r.add(tokens.subList(0, n));
        }
        return r;
    }

    public static String usage(String command, List<Token> tokens) {
        StringBuilder sb = new StringBuilder("/").append(command);
        for (Token t : tokens) sb.append(' ').append(t.display());
        return sb.toString();
    }
}
