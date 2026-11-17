package modstudio.ui.controls;

import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Code editor with line numbers and syntax highlighting for mcfunction, Java, JSON and JavaScript. */
public final class CodeEditor extends StackPane {
    private final CodeArea area = new CodeArea();
    private final Pattern pattern;
    private Consumer<String> onChange;
    private boolean silent;

    private static final String JAVA_KW = "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|record|return|short|static|super|switch|synchronized|this|throw|throws|try|var|void|volatile|while|true|false|null)\\b";
    private static final String JS_KW = "\\b(const|let|var|function|return|if|else|for|while|of|in|new|class|extends|import|from|export|default|true|false|null|undefined|this|switch|case|break|continue|async|await|try|catch)\\b";

    private static final String GLSL_KW = "\\b(attribute|const|uniform|varying|in|out|inout|layout|flat|smooth|centroid|precision|highp|mediump|lowp|break|continue|do|for|while|if|else|discard|return|switch|case|default|struct|void|bool|int|uint|float|double|vec2|vec3|vec4|ivec2|ivec3|ivec4|uvec2|uvec3|uvec4|bvec2|bvec3|bvec4|mat2|mat3|mat4|sampler2D|sampler3D|samplerCube|sampler2DShadow|isampler2D|usampler2D|true|false)\\b";

    public CodeEditor(String language) {
        this.pattern = switch (language) {
            case "java" -> Pattern.compile("(?<cmt>//[^\\n]*|/\\*(.|\\R)*?\\*/)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<ann>@[A-Za-z]+)|(?<kw>" + JAVA_KW + ")|(?<num>\\b\\d+(\\.\\d+)?[fFdDlL]?\\b)");
            case "js" -> Pattern.compile("(?<cmt>//[^\\n]*|/\\*(.|\\R)*?\\*/)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*'|`[^`]*`)|(?<kw>" + JS_KW + ")|(?<num>\\b\\d+(\\.\\d+)?\\b)");
            case "json" -> Pattern.compile("(?<key>\"([^\"\\\\\\n]|\\\\.)*\"(?=\\s*:))|(?<str>\"([^\"\\\\\\n]|\\\\.)*\")|(?<kw>\\b(true|false|null)\\b)|(?<num>-?\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b)");
            case "mcfunction" -> Pattern.compile("(?<cmt>^\\s*#[^\\n]*)|(?<kw>^\\s*[a-z_]+|\\b(run|if|unless|as|at|positioned|store|result|success|matches|entity|score|block|predicate)\\b)|(?<sel>@[aeprsn](\\[[^\\]\\n]*\\])?)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\")|(?<ann>#?[a-z0-9_.-]+:[a-z0-9_./-]+)|(?<num>[~^]-?\\d*(\\.\\d+)?|-?\\b\\d+(\\.\\d+)?\\b)", Pattern.MULTILINE);
            case "script" -> Pattern.compile("(?<cmt>^\\s*(#|//)[^\\n]*)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<kw>\\b(if|else|not)\\b)|(?<key>\\b[a-z_]+(?=\\s*=))|(?<ann>\\b[a-z_]+(?=\\s*\\())|(?<sel>@[aeprsn](\\[[^\\]\\n]*\\])?)|(?<num>-?\\b\\d+(\\.\\d+)?\\b)", Pattern.MULTILINE);
            case "glsl" -> Pattern.compile("(?<cmt>//[^\\n]*|/\\*(.|\\R)*?\\*/)|(?<ann>^\\s*#[a-z]+[^\\n]*)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\")|(?<kw>" + GLSL_KW + ")|(?<sel>\\b(texture|texelFetch|mix|clamp|smoothstep|step|normalize|dot|cross|length|distance|reflect|refract|pow|exp|log|sqrt|abs|sign|floor|ceil|fract|mod|min|max|sin|cos|tan|asin|acos|atan|radians|degrees|dFdx|dFdy|fwidth|textureSize|inverse|transpose)\\b)|(?<num>\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFuU]?\\b|\\.\\d+\\b)", Pattern.MULTILINE);
            default -> null;
        };
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        area.getStyleClass().add("code-area-ms");
        if (modstudio.ui.Ui.dark()) area.getStyleClass().add("dark");
        area.textProperty().addListener((o, a, b) -> {
            highlight();
            if (!silent && onChange != null) onChange.accept(b);
        });
        getChildren().add(new VirtualizedScrollPane<>(area));
        getStyleClass().add("code-editor");
        setMinHeight(160);
    }

    public void setOnTextChanged(Consumer<String> c) {
        this.onChange = c;
    }

    public void setText(String s) {
        silent = true;
        area.replaceText(s == null ? "" : s);
        area.moveTo(0);
        area.requestFollowCaret();
        area.getUndoManager().forgetHistory();
        silent = false;
    }

    public String getText() {
        return area.getText();
    }

    public void setEditable(boolean b) {
        area.setEditable(b);
    }

    public CodeArea area() {
        return area;
    }

    private void highlight() {
        if (pattern == null) return;
        String text = area.getText();
        if (text.length() > 400_000) return;
        area.setStyleSpans(0, compute(text));
    }

    private StyleSpans<Collection<String>> compute(String text) {
        Matcher m = pattern.matcher(text);
        int last = 0;
        StyleSpansBuilder<Collection<String>> b = new StyleSpansBuilder<>();
        while (m.find()) {
            String cls = m.group("kw") != null ? "kw"
                    : m.group("str") != null ? "str"
                    : group(m, "cmt") ? "cmt"
                    : group(m, "num") ? "num"
                    : group(m, "ann") ? "ann"
                    : group(m, "sel") ? "sel"
                    : group(m, "key") ? "key" : null;
            if (m.end() == m.start()) continue;
            b.add(Collections.emptyList(), m.start() - last);
            b.add(cls == null ? Collections.emptyList() : Collections.singleton(cls), m.end() - m.start());
            last = m.end();
        }
        b.add(Collections.emptyList(), text.length() - last);
        return b.create();
    }

    private boolean group(Matcher m, String name) {
        try {
            return m.group(name) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
