package modstudio.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Editor-side helpers for web requests: sends a test request with the same placeholder rules as the generated
 * runtime, follows JSON dot paths and previews cron schedules.
 */
public final class WebTest {
    private WebTest() {}

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(secret|player|uuid|uuid_plain|time)(?::([A-Za-z0-9_.-]+))?\\}");
    private static final Pattern PATH = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)\\]");
    private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record Result(int status, String body, long millis, String error, Map<String, String> extracted) {}

    public static String fill(String template, Map<String, String> vars, Secrets secrets, boolean mask) {
        return fill(template, vars, secrets, mask, 'r');
    }

    /** Same modes as the generated runtime: 'u' URL, 'j' JSON body, 'h' header, 'r' raw. */
    public static String fill(String template, Map<String, String> vars, Secrets secrets, boolean mask, char mode) {
        if (template == null || template.isEmpty()) return "";
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v;
            if (m.group(1).equals("secret")) {
                String name = m.group(2) == null ? "" : m.group(2);
                v = mask ? "****" : (secrets == null ? "" : secrets.get(name));
            } else {
                v = vars == null ? "" : vars.getOrDefault(m.group(1), "");
            }
            if (!mask || !m.group(1).equals("secret")) {
                v = switch (mode) {
                    case 'u' -> java.net.URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
                    case 'j' -> jsonEscape(v);
                    case 'h' -> v.replaceAll("[\\r\\n]", "");
                    default -> v;
                };
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String jsonEscape(String s) {
        String j = new com.google.gson.Gson().toJson(s);
        return j.substring(1, j.length() - 1);
    }

    public static Map<String, String> sampleVars(String player, String uuid) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("player", player);
        v.put("uuid", uuid);
        v.put("uuid_plain", uuid.replace("-", ""));
        v.put("time", Long.toString(System.currentTimeMillis() / 1000L));
        return v;
    }

    /** Sends the request synchronously (call from a background thread). */
    public static Result send(Systems.ApiRequest r, Secrets secrets, Map<String, String> vars) {
        long start = System.currentTimeMillis();
        try {
            String rawUrl = r.url == null ? "" : r.url.trim();
            if (rawUrl.isEmpty()) return new Result(-1, "", 0, "Empty request URL", Map.of());
            String method = (r.method == null || r.method.isBlank()) ? "GET" : r.method.trim().toUpperCase(Locale.ROOT);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(fill(rawUrl, vars, secrets, false, 'u')))
                    .timeout(Duration.ofSeconds(Math.max(1, r.timeout)));
            boolean contentType = false;
            String hdrs = r.headers == null ? "" : r.headers;
            for (String line : hdrs.split("\\R")) {
                int c = line.indexOf(':');
                if (c <= 0) continue;
                String key = line.substring(0, c).trim();
                contentType |= key.equalsIgnoreCase("content-type");
                b.header(key, fill(line.substring(c + 1).trim(), vars, secrets, false, 'h'));
            }
            String rawBody = r.body == null ? "" : r.body.trim();
            String body = fill(r.body, vars, secrets, false, rawBody.startsWith("{") || rawBody.startsWith("[") ? 'j' : 'r');
            boolean hasBody = !body.isEmpty() && !method.equals("GET") && !method.equals("DELETE");
            if (hasBody && !contentType) {
                String t = body.trim();
                b.header("Content-Type", t.startsWith("{") || t.startsWith("[") ? "application/json" : "text/plain; charset=utf-8");
            }
            b.method(method, hasBody ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
            HttpResponse<String> res = SHARED_CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String text = res.body() == null ? "" : res.body();
            return new Result(res.statusCode(), text, System.currentTimeMillis() - start, "", extract(r, text));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            String msg = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return new Result(-1, "", System.currentTimeMillis() - start, msg, Map.of());
        }
    }

    public static Map<String, String> extract(Systems.ApiRequest r, String body) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonElement json = null;
        try {
            if (!body.isBlank()) json = JsonParser.parseString(body);
        } catch (RuntimeException ignored) {
        }
        for (Systems.Extract x : r.extract) {
            if (x.key.isBlank()) continue;
            JsonElement v = json == null ? null : path(json, x.path);
            out.put(x.key, v == null || v.isJsonNull() ? "" : v.isJsonPrimitive() ? v.getAsString() : v.toString());
        }
        return out;
    }

    public static JsonElement path(JsonElement root, String path) {
        JsonElement cur = root;
        Matcher m = PATH.matcher(path == null ? "" : path.trim());
        while (m.find()) {
            if (cur == null || cur.isJsonNull()) return null;
            if (m.group(1) != null) {
                if (!cur.isJsonObject()) return null;
                cur = cur.getAsJsonObject().get(m.group(1));
            } else {
                if (!cur.isJsonArray()) return null;
                JsonArray a = cur.getAsJsonArray();
                int i;
                try {
                    i = Integer.parseInt(m.group(2));
                } catch (NumberFormatException ex) {
                    return null;
                }
                cur = i >= 0 && i < a.size() ? a.get(i) : null;
            }
        }
        return cur;
    }

    // ------------------------------------------------------------------ cron

    public static boolean cronValid(String expr) {
        if (expr == null) return false;
        String[] f = expr.trim().split("\\s+");
        if (f.length != 5) return false;
        int[][] ranges = {{0, 59}, {0, 23}, {1, 31}, {1, 12}, {0, 7}};
        for (int i = 0; i < 5; i++) {
            for (String part : f[i].split(",")) {
                if (!part.matches("(\\*|\\d+(-\\d+)?)(/\\d+)?")) return false;
                String rangePart = part.contains("/") ? part.substring(0, part.indexOf('/')) : part;
                if (rangePart.contains("-")) {
                    String[] bounds = rangePart.split("-");
                    try {
                        int a = Integer.parseInt(bounds[0]), b = Integer.parseInt(bounds[1]);
                        if (a > b || a < ranges[i][0] || b > ranges[i][1]) return false;
                    } catch (NumberFormatException ex) {
                        return false;
                    }
                }
                for (String n : part.split("[-/*]")) {
                    if (n.isEmpty()) continue;
                    int v;
                    try {
                        v = Integer.parseInt(n);
                    } catch (NumberFormatException ex) {
                        return false;
                    }
                    if (part.indexOf('/') >= 0 && part.endsWith("/" + n)) {
                        if (v < 1) return false;
                        continue;
                    }
                    if (v < ranges[i][0] || v > ranges[i][1]) return false;
                }
            }
        }
        return true;
    }

    public static boolean cronMatches(String expr, LocalDateTime t) {
        try {
            String[] f = expr.trim().split("\\s+");
            if (f.length != 5) return false;
            int dow = t.getDayOfWeek().getValue() % 7;
            boolean dom = field(f[2], t.getDayOfMonth(), 1, 31);
            boolean dw = field(f[4], dow, 0, 7) || (dow == 0 && field(f[4], 7, 0, 7));
            boolean day = f[2].equals("*") || f[4].equals("*") ? dom && dw : dom || dw;
            return day && field(f[0], t.getMinute(), 0, 59) && field(f[1], t.getHour(), 0, 23) && field(f[3], t.getMonthValue(), 1, 12);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean field(String f, int v, int min, int max) {
        for (String part : f.split(",")) {
            int step = 1;
            String range = part;
            int slash = part.indexOf('/');
            if (slash >= 0) {
                step = Math.max(1, Integer.parseInt(part.substring(slash + 1)));
                range = part.substring(0, slash);
            }
            int lo, hi;
            if (range.equals("*")) {
                lo = min;
                hi = max;
            } else if (range.contains("-")) {
                String[] ab = range.split("-");
                lo = Integer.parseInt(ab[0]);
                hi = Integer.parseInt(ab[1]);
            } else {
                lo = Integer.parseInt(range);
                hi = slash >= 0 ? max : lo;
            }
            if (v >= lo && v <= hi && (v - lo) % step == 0) return true;
        }
        return false;
    }

    /** The next times (to the minute) the expression fires, searching up to about a year ahead. */
    public static List<LocalDateTime> nextRuns(String expr, LocalDateTime from, int count) {
        List<LocalDateTime> r = new ArrayList<>();
        if (!cronValid(expr)) return r;
        LocalDateTime t = from.withSecond(0).withNano(0).plusMinutes(1);
        for (int i = 0; i < 2_103_840 && r.size() < count; i++, t = t.plusMinutes(1)) {
            if (cronMatches(expr, t)) r.add(t);
        }
        return r;
    }
}
