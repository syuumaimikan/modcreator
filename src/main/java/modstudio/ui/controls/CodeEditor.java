package modstudio.ui.controls;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Code editor with line numbers, syntax highlighting, find/replace, bracket matching, auto-indent and autocomplete. */
public final class CodeEditor extends StackPane {
    private final CodeArea area = new CodeArea();
    private String language;
    private Pattern pattern;
    private Consumer<String> onChange;
    private boolean silent;
    private int[] lastBrackets;

    private final HBox findBar = new HBox(6);
    private final TextField findField = new TextField();
    private final TextField replaceField = new TextField();
    private final CheckBox matchCase = new CheckBox(I18n.t("code.match_case"));
    private final Label matchLabel = Ui.muted("");
    private final List<NodePair> replaceControls = new ArrayList<>();

    private final Popup acPopup = new Popup();
    private final ListView<String> acList = new ListView<>();
    private int acPrefixStart;

    private record NodePair(javafx.scene.Node node) {}

    private static final String JAVA_KW = "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|record|return|short|static|super|switch|synchronized|this|throw|throws|try|var|void|volatile|while|true|false|null)\\b";
    private static final String KOTLIN_KW = "\\b(abstract|actual|annotation|as|break|by|catch|class|companion|const|constructor|continue|crossinline|data|do|dynamic|else|enum|expect|external|false|final|finally|for|fun|get|if|import|in|infix|init|inline|inner|interface|internal|is|lateinit|noinline|null|object|open|operator|out|override|package|private|protected|public|reified|return|sealed|set|super|suspend|tailrec|this|throw|true|try|typealias|val|var|vararg|when|where|while)\\b";
    private static final String SCALA_KW = "\\b(abstract|case|catch|class|def|do|else|enum|export|extends|false|final|finally|for|given|if|implicit|import|lazy|match|new|null|object|override|package|private|protected|return|sealed|super|then|this|throw|trait|true|try|type|using|val|var|while|with|yield)\\b";
    private static final String GROOVY_KW = "\\b(abstract|as|assert|boolean|break|byte|case|catch|char|class|const|continue|def|default|do|double|else|enum|extends|false|final|finally|float|for|if|implements|import|in|instanceof|int|interface|long|native|new|null|package|private|protected|public|return|short|static|super|switch|synchronized|this|throw|throws|trait|true|try|void|while)\\b";
    private static final String JS_KW = "\\b(abstract|any|as|async|await|boolean|break|case|catch|class|const|continue|declare|default|delete|do|else|enum|export|extends|false|finally|for|from|function|get|if|implements|import|in|instanceof|interface|keyof|let|module|namespace|never|new|null|number|of|override|private|protected|public|readonly|return|set|static|string|super|switch|this|throw|true|try|type|typeof|undefined|unknown|var|void|while|yield)\\b";
    private static final String PY_KW = "\\b(False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|self|try|while|with|yield)\\b";
    private static final String LUA_KW = "\\b(and|break|do|else|elseif|end|false|for|function|goto|if|in|local|nil|not|or|repeat|return|then|true|until|while)\\b";
    private static final String GLSL_KW = "\\b(attribute|const|uniform|varying|in|out|inout|layout|flat|smooth|centroid|precision|highp|mediump|lowp|break|continue|do|for|while|if|else|discard|return|switch|case|default|struct|void|bool|int|uint|float|double|vec2|vec3|vec4|ivec2|ivec3|ivec4|uvec2|uvec3|uvec4|bvec2|bvec3|bvec4|mat2|mat3|mat4|sampler2D|sampler3D|samplerCube|sampler2DShadow|isampler2D|usampler2D|true|false)\\b";

    public CodeEditor(String language) {
        this.language = normalizeLang(language);
        this.pattern = compilePattern(this.language);
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        area.getStyleClass().add("code-area-ms");
        if (Ui.dark()) area.getStyleClass().add("dark");
        area.textProperty().addListener((o, a, b) -> {
            highlight();
            updateFindCount();
            if (!silent && onChange != null) onChange.accept(b);
            if (acPopup.isShowing()) refreshAutocomplete();
        });
        area.caretPositionProperty().addListener((o, a, b) -> {
            int[] br = matchedBrackets(area.getText(), b);
            if (!Arrays.equals(lastBrackets, br)) {
                lastBrackets = br;
                highlight();
            }
        });

        setupFindBar();
        setupAutocomplete();
        setupKeys();

        VirtualizedScrollPane<CodeArea> sp = new VirtualizedScrollPane<>(area);
        VBox.setVgrow(sp, Priority.ALWAYS);
        VBox box = new VBox(findBar, sp);
        getChildren().add(box);
        getStyleClass().add("code-editor");
        setMinHeight(160);
    }

    private void setupFindBar() {
        findBar.getStyleClass().add("find-bar");
        findBar.setAlignment(Pos.CENTER_LEFT);
        findBar.setVisible(false);
        findBar.setManaged(false);
        findField.setPromptText(I18n.t("code.find"));
        findField.setPrefWidth(160);
        replaceField.setPromptText(I18n.t("code.replace"));
        replaceField.setPrefWidth(150);

        Button prev = Ui.iconButton("mdi2c-chevron-up", I18n.t("code.find_prev"), () -> findNext(false));
        Button next = Ui.iconButton("mdi2c-chevron-down", I18n.t("code.find_next"), () -> findNext(true));
        Button repOne = Ui.button(I18n.t("code.replace"), null, this::replaceOne);
        Button repAll = Ui.button(I18n.t("code.replace_all"), null, this::replaceAll);
        Button close = Ui.iconButton("mdi2c-close", I18n.t("action.close"), this::hideFindBar);

        replaceControls.add(new NodePair(replaceField));
        replaceControls.add(new NodePair(repOne));
        replaceControls.add(new NodePair(repAll));

        findField.textProperty().addListener((o, a, b) -> updateFindCount());
        matchCase.selectedProperty().addListener((o, a, b) -> updateFindCount());
        findField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                findNext(!e.isShiftDown());
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hideFindBar();
                e.consume();
            }
        });
        replaceField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                replaceOne();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hideFindBar();
                e.consume();
            }
        });

        findBar.getChildren().addAll(findField, prev, next, matchCase, matchLabel, replaceField, repOne, repAll, Ui.spacer(), close);
    }

    private void showFindBar(boolean withReplace) {
        String sel = area.getSelectedText();
        if (sel != null && !sel.isEmpty() && !sel.contains("\n")) findField.setText(sel);
        boolean editable = area.isEditable();
        for (NodePair np : replaceControls) {
            boolean show = withReplace && editable;
            np.node.setVisible(show);
            np.node.setManaged(show);
        }
        findBar.setVisible(true);
        findBar.setManaged(true);
        updateFindCount();
        findField.requestFocus();
        findField.selectAll();
    }

    private void hideFindBar() {
        findBar.setVisible(false);
        findBar.setManaged(false);
        area.requestFocus();
    }

    private void updateFindCount() {
        if (!findBar.isVisible()) return;
        String q = findField.getText();
        if (q.isEmpty()) {
            matchLabel.setText("");
            return;
        }
        String text = area.getText();
        String hay = matchCase.isSelected() ? text : text.toLowerCase(Locale.ROOT);
        String needle = matchCase.isSelected() ? q : q.toLowerCase(Locale.ROOT);
        int count = 0, idx = 0;
        while ((idx = hay.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        matchLabel.setText(String.valueOf(count));
    }

    private void findNext(boolean forward) {
        String q = findField.getText();
        String text = area.getText();
        if (q.isEmpty() || text.isEmpty()) return;
        String hay = matchCase.isSelected() ? text : text.toLowerCase(Locale.ROOT);
        String needle = matchCase.isSelected() ? q : q.toLowerCase(Locale.ROOT);
        int found;
        if (forward) {
            int start = Math.max(area.getCaretPosition(), area.getSelection().getEnd());
            found = hay.indexOf(needle, start);
            if (found < 0) found = hay.indexOf(needle, 0);
        } else {
            int start = Math.max(0, area.getSelection().getStart() - 1);
            found = hay.lastIndexOf(needle, start);
            if (found < 0) found = hay.lastIndexOf(needle);
        }
        if (found >= 0) {
            area.selectRange(found, found + needle.length());
            area.requestFollowCaret();
        }
    }

    private void replaceOne() {
        if (!area.isEditable()) return;
        String q = findField.getText();
        if (q.isEmpty()) return;
        String sel = area.getSelectedText();
        boolean eq = matchCase.isSelected() ? sel.equals(q) : sel.equalsIgnoreCase(q);
        if (eq) {
            int start = area.getSelection().getStart();
            String rep = replaceField.getText();
            area.replaceText(start, area.getSelection().getEnd(), rep);
            area.moveTo(start + rep.length());
        }
        findNext(true);
    }

    private void replaceAll() {
        if (!area.isEditable()) return;
        String q = findField.getText();
        if (q.isEmpty()) return;
        String rep = replaceField.getText();
        int flags = matchCase.isSelected() ? Pattern.LITERAL : Pattern.LITERAL | Pattern.CASE_INSENSITIVE;
        String updated = Pattern.compile(q, flags).matcher(area.getText()).replaceAll(Matcher.quoteReplacement(rep));
        area.replaceText(updated);
        updateFindCount();
    }

    private void setupAutocomplete() {
        acList.setPrefSize(260, 170);
        acPopup.getContent().add(acList);
        acPopup.setAutoHide(true);
        acList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) insertAutocomplete();
        });
        acList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
                insertAutocomplete();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                acPopup.hide();
                e.consume();
            }
        });
    }

    private void showAutocomplete() {
        if (!area.isEditable()) return;
        if (!refreshAutocomplete()) return;
        var bounds = area.getCaretBounds();
        if (bounds.isPresent()) {
            acPopup.show(area, bounds.get().getMaxX(), bounds.get().getMaxY() + 2);
        }
    }

    private boolean refreshAutocomplete() {
        String text = area.getText();
        int caret = area.getCaretPosition();
        int start = caret;
        while (start > 0 && (Character.isJavaIdentifierPart(text.charAt(start - 1)) || text.charAt(start - 1) == ':')) {
            start--;
        }
        acPrefixStart = start;
        String prefix = text.substring(start, caret).toLowerCase(Locale.ROOT);
        List<String> matches = completions().stream()
                .filter(s -> prefix.isEmpty() || s.toLowerCase(Locale.ROOT).contains(prefix))
                .limit(25)
                .toList();
        if (matches.isEmpty()) {
            acPopup.hide();
            return false;
        }
        acList.getItems().setAll(matches);
        acList.getSelectionModel().select(0);
        return true;
    }

    private void insertAutocomplete() {
        String pick = acList.getSelectionModel().getSelectedItem();
        if (pick == null) return;
        int caret = area.getCaretPosition();
        area.replaceText(acPrefixStart, caret, pick);
        acPopup.hide();
        area.requestFocus();
    }

    private List<String> completions() {
        return switch (language) {
            case "java" -> List.of("public", "private", "protected", "static", "final", "abstract", "interface", "record", "return", "void",
                    "@Override", "@SubscribeEvent", "@Mixin", "@Shadow", "@Inject", "@Redirect", "@ModifyArg", "@ModifyVariable", "@Accessor", "@Invoker", "@Unique",
                    "CallbackInfo", "CallbackInfoReturnable", "ItemStack", "Level", "ServerLevel", "BlockPos", "BlockState", "BlockEntity",
                    "Player", "ServerPlayer", "LivingEntity", "Mob", "PathfinderMob", "Goal", "Entity", "Component.literal(\"\")",
                    "InteractionResult.SUCCESS", "InteractionResultHolder", "ResourceLocation", "BuiltInRegistries", "MinecraftServer",
                    "CommandSourceStack", "CommandDispatcher", "Minecraft", "GuiGraphics", "CompletableFuture", "HttpClient",
                    "ModBlocks", "ModItems", "ModEntities", "ModProcedures", "ModJavaHandlers");
            case "kotlin" -> List.of("package ", "import ", "object ", "class ", "fun ", "val ", "var ", "override fun ", "companion object", "@JvmStatic",
                    "when (", "ItemStack", "Level", "ServerLevel", "Player", "ServerPlayer", "Component.literal(\"\")", "MinecraftServer",
                    "CommandDispatcher<CommandSourceStack>");
            case "scala" -> List.of("package ", "import ", "object ", "class ", "trait ", "def ", "val ", "var ", "override def ", "match {", "case ", "Unit");
            case "groovy" -> List.of("package ", "import ", "class ", "def ", "static void init()", "MinecraftServer", "Component.literal(\"\")");
            case "python" -> List.of("def ", "class ", "import ", "from ", "if __name__ == \"__main__\":", "async def ", "return ", "print(");
            case "lua" -> List.of("local ", "function ", "local function ", "end", "if ", "then", "elseif ", "else", "for ", "while ", "return ");
            case "toml" -> List.of("[mod]", "name = \"\"", "version = \"1.0.0\"", "enabled = true");
            case "yaml" -> List.of("name: ", "version: ", "enabled: true", "items:");
            case "glsl" -> List.of("#version 150", "uniform sampler2D InSampler;", "uniform sampler2D DiffuseSampler;",
                    "in vec2 texCoord;", "out vec4 fragColor;", "vec2", "vec3", "vec4", "mat4",
                    "texture(InSampler, texCoord)", "mix(", "clamp(", "smoothstep(", "normalize(", "dot(", "length(");
            case "mcfunction" -> List.of("execute as @a at @s run ", "give @s ", "tp @s ~ ~ ~",
                    "summon ", "setblock ~ ~ ~ ", "fill ", "particle ", "playsound ", "title @a title ",
                    "scoreboard objectives ", "tag @s add ", "effect give @s ");
            case "js", "javascript", "typescript" -> List.of("import { world, system } from \"@minecraft/server\";", "world.sendMessage(",
                    "world.afterEvents.", "world.beforeEvents.", "system.runInterval(", "system.runTimeout(",
                    "export ", "interface ", "const ", "let ", "function ", "async function ", "return ");
            case "json" -> List.of("\"parent\": ", "\"textures\": ", "\"elements\": ", "\"pools\": ",
                    "\"entries\": ", "\"conditions\": ", "\"components\": ", "\"type\": ");
            default -> List.of();
        };
    }

    public void setLanguage(String language) {
        String norm = normalizeLang(language);
        if (norm.equals(this.language)) return;
        this.language = norm;
        this.pattern = compilePattern(norm);
        highlight();
    }

    private static String normalizeLang(String language) {
        if (language == null) return "";
        String l = language.trim().toLowerCase(Locale.ROOT);
        return switch (l) {
            case "javascript" -> "js";
            case "ts" -> "typescript";
            case "py" -> "python";
            case "kt", "kts" -> "kotlin";
            case "yml" -> "yaml";
            default -> l;
        };
    }

    private static Pattern compilePattern(String language) {
        return switch (language) {
            case "java" -> Pattern.compile("(?<cmt>//[^\\n]*|/\\*(.|\\R)*?\\*/)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<ann>@[A-Za-z]+)|(?<kw>" + JAVA_KW + ")|(?<num>\\b\\d+(\\.\\d+)?[fFdDlL]?\\b)");
            case "kotlin" -> Pattern.compile("(?<cmt>//[^\\n]*|/\\*(.|\\R)*?\\*/)|(?<str>\"\"\"(.|\\R)*?\"\"\"|\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<ann>@[A-Za-z_][A-Za-z0-9_]*)|(?<kw>" + KOTLIN_KW + ")|(?<num>\\b\\d+([._]\\d+)*[fFdDlLuU]?\\b)");
            case "scala" -> Pattern.compile("(?<cmt>//[^\\n]*|/\\*(.|\\R)*?\\*/)|(?<str>\"\"\"(.|\\R)*?\"\"\"|\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<ann>@[A-Za-z_][A-Za-z0-9_]*)|(?<kw>" + SCALA_KW + ")|(?<num>\\b\\d+([._]\\d+)*[fFdDlL]?\\b)");
            case "groovy" -> Pattern.compile("(?<cmt>//[^\\n]*|/\\*(.|\\R)*?\\*/)|(?<str>\"\"\"(.|\\R)*?\"\"\"|'''(.|\\R)*?'''|\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<ann>@[A-Za-z_][A-Za-z0-9_]*)|(?<kw>" + GROOVY_KW + ")|(?<num>\\b\\d+([._]\\d+)*[gGfFdDlL]?\\b)");
            case "js", "typescript" -> Pattern.compile("(?<cmt>//[^\\n]*|/\\*(.|\\R)*?\\*/)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*'|`[^`]*`)|(?<ann>@[A-Za-z_][A-Za-z0-9_]*)|(?<kw>" + JS_KW + ")|(?<num>\\b\\d+(\\.\\d+)?\\b)");
            case "python" -> Pattern.compile("(?<cmt>#[^\\n]*)|(?<str>\"\"\"(.|\\R)*?\"\"\"|'''(.|\\R)*?'''|\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<ann>@[A-Za-z_][A-Za-z0-9_.]*)|(?<kw>" + PY_KW + ")|(?<num>\\b\\d+(\\.\\d+)?([jJ])?\\b)");
            case "lua" -> Pattern.compile("(?<cmt>--\\[\\[(.|\\R)*?\\]\\]|--[^\\n]*)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<kw>" + LUA_KW + ")|(?<num>\\b\\d+(\\.\\d+)?\\b)");
            case "toml" -> Pattern.compile("(?<cmt>#[^\\n]*)|(?<kw>^\\s*\\[[^\\]\\n]+\\])|(?<key>^\\s*[A-Za-z0-9_.-]+(?=\\s*=))|(?<str>\"\"\"(.|\\R)*?\"\"\"|\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<ann>\\b(true|false)\\b)|(?<num>-?\\b\\d+(\\.\\d+)?\\b)", Pattern.MULTILINE);
            case "yaml" -> Pattern.compile("(?<cmt>#[^\\n]*)|(?<key>^\\s*-?\\s*[A-Za-z0-9_.-]+(?=\\s*:))|(?<str>\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<kw>\\b(true|false|null|~)\\b)|(?<num>-?\\b\\d+(\\.\\d+)?\\b)", Pattern.MULTILINE);
            case "xml" -> Pattern.compile("(?<cmt><!--(.|\\R)*?-->)|(?<kw></?[A-Za-z0-9_:-]+|/?>)|(?<key>\\b[A-Za-z0-9_:-]+(?=\\s*=))|(?<str>\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')");
            case "properties" -> Pattern.compile("(?<cmt>^\\s*[#!][^\\n]*)|(?<key>^\\s*[A-Za-z0-9_.-]+(?=\\s*[=:]))", Pattern.MULTILINE);
            case "json" -> Pattern.compile("(?<key>\"([^\"\\\\\\n]|\\\\.)*\"(?=\\s*:))|(?<str>\"([^\"\\\\\\n]|\\\\.)*\")|(?<kw>\\b(true|false|null)\\b)|(?<num>-?\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b)");
            case "mcfunction" -> Pattern.compile("(?<cmt>^\\s*#[^\\n]*)|(?<kw>^\\s*[a-z_]+|\\b(run|if|unless|as|at|positioned|store|result|success|matches|entity|score|block|predicate)\\b)|(?<sel>@[aeprsn](\\[[^\\]\\n]*\\])?)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\")|(?<ann>#?[a-z0-9_.-]+:[a-z0-9_./-]+)|(?<num>[~^]-?\\d*(\\.\\d+)?|-?\\b\\d+(\\.\\d+)?\\b)", Pattern.MULTILINE);
            case "script" -> Pattern.compile("(?<cmt>^\\s*(#|//)[^\\n]*)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\"|'([^'\\\\\\n]|\\\\.)*')|(?<kw>\\b(if|else|not)\\b)|(?<key>\\b[a-z_]+(?=\\s*=))|(?<ann>\\b[a-z_]+(?=\\s*\\())|(?<sel>@[aeprsn](\\[[^\\]\\n]*\\])?)|(?<num>-?\\b\\d+(\\.\\d+)?\\b)", Pattern.MULTILINE);
            case "glsl" -> Pattern.compile("(?<cmt>//[^\\n]*|/\\*(.|\\R)*?\\*/)|(?<ann>^\\s*#[a-z]+[^\\n]*)|(?<str>\"([^\"\\\\\\n]|\\\\.)*\")|(?<kw>" + GLSL_KW + ")|(?<sel>\\b(texture|texelFetch|mix|clamp|smoothstep|step|normalize|dot|cross|length|distance|reflect|refract|pow|exp|log|sqrt|abs|sign|floor|ceil|fract|mod|min|max|sin|cos|tan|asin|acos|atan|radians|degrees|dFdx|dFdy|fwidth|textureSize|inverse|transpose)\\b)|(?<num>\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFuU]?\\b|\\.\\d+\\b)", Pattern.MULTILINE);
            default -> null;
        };
    }

    private void setupKeys() {
        KeyCombination findKey = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
        KeyCombination repKey = new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN);
        KeyCombination acKey = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);
        area.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (findKey.match(e)) {
                showFindBar(false);
                e.consume();
            } else if (repKey.match(e)) {
                showFindBar(true);
                e.consume();
            } else if (acKey.match(e)) {
                showAutocomplete();
                e.consume();
            } else if (acPopup.isShowing()) {
                if (e.getCode() == KeyCode.DOWN) {
                    acList.getSelectionModel().selectNext();
                    acList.scrollTo(acList.getSelectionModel().getSelectedIndex());
                    e.consume();
                } else if (e.getCode() == KeyCode.UP) {
                    acList.getSelectionModel().selectPrevious();
                    acList.scrollTo(acList.getSelectionModel().getSelectedIndex());
                    e.consume();
                } else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
                    insertAutocomplete();
                    e.consume();
                } else if (e.getCode() == KeyCode.ESCAPE) {
                    acPopup.hide();
                    e.consume();
                }
            } else if (e.getCode() == KeyCode.ENTER && area.isEditable() && !e.isControlDown() && !e.isShiftDown()) {
                handleAutoIndent();
                e.consume();
            }
        });
    }

    private void handleAutoIndent() {
        int caret = area.getCaretPosition();
        String text = area.getText();
        int lineStart = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        StringBuilder indent = new StringBuilder();
        for (int i = lineStart; i < caret; i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\t') indent.append(ch);
            else break;
        }
        char before = caret > 0 ? text.charAt(caret - 1) : 0;
        char after = caret < text.length() ? text.charAt(caret) : 0;
        if (before == '{' || before == '[') {
            String inner = indent + "    ";
            if ((before == '{' && after == '}') || (before == '[' && after == ']')) {
                area.replaceSelection("\n" + inner + "\n" + indent);
                area.moveTo(caret + 1 + inner.length());
            } else {
                area.replaceSelection("\n" + inner);
            }
        } else {
            area.replaceSelection("\n" + indent);
        }
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
        String text = area.getText();
        if (text.length() > 400_000) return;
        StyleSpans<Collection<String>> spans = pattern == null ? plainSpans(text.length()) : compute(text);
        int[] br = lastBrackets != null ? lastBrackets : matchedBrackets(text, area.getCaretPosition());
        if (br != null && br[0] >= 0 && br[1] >= 0 && br[0] < text.length() && br[1] < text.length()) {
            spans = overlayMatch(spans, br[0], br[1]);
        }
        area.setStyleSpans(0, spans);
    }

    private StyleSpans<Collection<String>> plainSpans(int len) {
        StyleSpansBuilder<Collection<String>> b = new StyleSpansBuilder<>();
        b.add(Collections.emptyList(), len);
        return b.create();
    }

    private StyleSpans<Collection<String>> overlayMatch(StyleSpans<Collection<String>> spans, int p1, int p2) {
        StyleSpansBuilder<Collection<String>> b = new StyleSpansBuilder<>();
        int lo = Math.min(p1, p2), hi = Math.max(p1, p2);
        int pos = 0;
        for (StyleSpan<Collection<String>> sp : spans) {
            int start = pos, end = pos + sp.getLength();
            if (end <= lo || start > hi || (lo >= end && hi < start)) {
                b.add(sp.getStyle(), sp.getLength());
            } else {
                int cur = start;
                for (int pt : new int[]{lo, hi}) {
                    if (pt >= cur && pt < end) {
                        if (pt > cur) b.add(sp.getStyle(), pt - cur);
                        List<String> merged = new ArrayList<>(sp.getStyle());
                        merged.add("match");
                        b.add(merged, 1);
                        cur = pt + 1;
                    }
                }
                if (cur < end) b.add(sp.getStyle(), end - cur);
            }
            pos = end;
        }
        return b.create();
    }

    private static int[] matchedBrackets(String text, int caret) {
        if (text == null || text.isEmpty()) return null;
        int idx = -1;
        if (caret < text.length() && "()[]{}".indexOf(text.charAt(caret)) >= 0) idx = caret;
        else if (caret > 0 && "()[]{}".indexOf(text.charAt(caret - 1)) >= 0) idx = caret - 1;
        if (idx < 0) return null;
        char c = text.charAt(idx);
        char match;
        int dir;
        switch (c) {
            case '(' -> { match = ')'; dir = 1; }
            case '[' -> { match = ']'; dir = 1; }
            case '{' -> { match = '}'; dir = 1; }
            case ')' -> { match = '('; dir = -1; }
            case ']' -> { match = '['; dir = -1; }
            default -> { match = '{'; dir = -1; }
        }
        int depth = 0;
        for (int i = idx; i >= 0 && i < text.length(); i += dir) {
            char cur = text.charAt(i);
            if (cur == c) depth++;
            else if (cur == match) {
                depth--;
                if (depth == 0) return new int[]{idx, i};
            }
        }
        return null;
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
