package modstudio.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Window;
import modstudio.core.ElementType;
import modstudio.i18n.I18n;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Small UI helpers shared by every view. */
public final class Ui {
    private Ui() {}

    public static final PseudoClass DANGER = PseudoClass.getPseudoClass("danger");
    private static String theme = "light";

    // ------------------------------------------------------------------ theme

    public static void applyTheme(String t) {
        theme = "dark".equals(t) ? "dark" : "light";
        Application.setUserAgentStylesheet(theme.equals("dark")
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    public static boolean dark() {
        return theme.equals("dark");
    }

    public static String css() {
        return Ui.class.getResource("/modstudio/ui/app.css").toExternalForm();
    }

    public static void style(Scene scene) {
        scene.getStylesheets().setAll(css());
        styleRoot(scene.getRoot());
    }

    public static void styleRoot(Parent root) {
        String font = uiFont();
        if (font != null) root.setStyle("-fx-font-family: \"" + font + "\";");
    }

    /** Picks a font that renders the UI language well on this system. */
    public static String uiFont() {
        List<String> prefs = switch (I18n.language()) {
            case "ja" -> List.of("Yu Gothic UI", "Meiryo UI", "Meiryo", "Hiragino Sans", "Noto Sans CJK JP", "Noto Sans JP");
            case "zh" -> List.of("Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Noto Sans SC");
            case "ko" -> List.of("Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans CJK KR", "Noto Sans KR");
            default -> List.of("Segoe UI", "Helvetica Neue", "Noto Sans", "Inter");
        };
        List<String> families = Font.getFamilies();
        for (String f : prefs) if (families.contains(f)) return f;
        return null;
    }

    public static void styleDialog(Dialog<?> d, Window owner) {
        if (owner != null) d.initOwner(owner);
        d.getDialogPane().getStylesheets().setAll(css());
        styleRoot(d.getDialogPane());
    }

    // ------------------------------------------------------------------ icons

    public static FontIcon icon(String code) {
        FontIcon i = new FontIcon(code);
        i.getStyleClass().add("ui-icon");
        return i;
    }

    public static FontIcon icon(String code, int size) {
        FontIcon i = icon(code);
        i.getStyleClass().add("icon-" + size);
        return i;
    }

    public static String typeIcon(ElementType t) {
        return switch (t) {
            case ITEM -> "mdi2d-diamond-stone";
            case BLOCK -> "mdi2c-cube-outline";
            case ARMOR_MATERIAL -> "mdi2s-shield-half-full";
            case STATUS_EFFECT -> "mdi2f-flask-outline";
            case ENCHANTMENT -> "mdi2b-book-open-variant";
            case DAMAGE_TYPE -> "mdi2h-heart-broken-outline";
            case PAINTING -> "mdi2i-image-frame";
            case SONG -> "mdi2a-album";
            case MODEL -> "mdi2r-rotate-3d-variant";
            case CREATIVE_TAB -> "mdi2t-tab";
            case SOUND -> "mdi2m-music-note-outline";
            case RECIPE -> "mdi2g-grid";
            case LOOT_TABLE -> "mdi2t-treasure-chest";
            case ADVANCEMENT -> "mdi2t-trophy-outline";
            case TAG -> "mdi2t-tag-outline";
            case FUNCTION -> "mdi2f-function-variant";
            case PROCEDURE -> "mdi2l-lightning-bolt-outline";
            case COMMAND -> "mdi2c-console-line";
            case CODE_FILE -> "mdi2c-code-braces";
            case MOB -> "mdi2g-ghost-outline";
            case EFFECT -> "mdi2s-shimmer";
            case STRUCTURE -> "mdi2h-home-city-outline";
            case BIOME -> "mdi2p-pine-tree";
            case DIMENSION -> "mdi2e-earth";
            case MENU -> "mdi2v-view-grid-outline";
            case HUD -> "mdi2m-monitor-dashboard";
            case API_REQUEST -> "mdi2a-api";
            case RESOURCE -> "mdi2f-flash-outline";
            case MACHINE -> "mdi2c-cog-outline";
            case MULTIBLOCK -> "mdi2c-cube-scan";
            case SHADER -> "mdi2b-blur";
            case CUTSCENE -> "mdi2m-movie-open-outline";
        };
    }

    // ------------------------------------------------------------------ controls

    public static Button button(String text, String icon, Runnable action) {
        Button b = new Button(text);
        if (icon != null) b.setGraphic(icon(icon));
        b.setOnAction(e -> action.run());
        return b;
    }

    public static Button accent(String text, String icon, Runnable action) {
        Button b = button(text, icon, action);
        b.getStyleClass().add("accent");
        return b;
    }

    public static Button iconButton(String icon, String tooltip, Runnable action) {
        Button b = new Button();
        b.setGraphic(icon(icon));
        b.getStyleClass().addAll("button-icon", "flat");
        if (tooltip != null) b.setTooltip(new Tooltip(tooltip));
        b.setOnAction(e -> action.run());
        return b;
    }

    public static Label label(String text, String... classes) {
        Label l = new Label(text);
        l.getStyleClass().addAll(classes);
        return l;
    }

    public static Label muted(String text) {
        Label l = label(text, "text-muted");
        l.setWrapText(true);
        return l;
    }

    /** Error text that takes no space while empty. */
    public static Label errorLabel() {
        Label l = label("", "error-text", "text-small");
        l.setWrapText(true);
        l.managedProperty().bind(l.textProperty().isNotEmpty());
        l.visibleProperty().bind(l.textProperty().isNotEmpty());
        return l;
    }

    public static Label help(String text) {
        Label l = label(text, "help-text");
        l.setWrapText(true);
        // Without this a wrapped label in a VBox can be squeezed to one line and cut off with an ellipsis.
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static HBox hbox(double spacing, Node... nodes) {
        HBox h = new HBox(spacing, nodes);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    public static VBox vbox(double spacing, Node... nodes) {
        return new VBox(spacing, nodes);
    }

    public static VBox card(String title, Node... content) {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");
        if (title != null) box.getChildren().add(label(title, "card-title"));
        box.getChildren().addAll(content);
        return box;
    }

    public static ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("edge-to-edge");
        return sp;
    }

    public static <T> ComboBox<T> combo(List<T> items, Function<T, String> display) {
        ComboBox<T> c = new ComboBox<>();
        c.getItems().setAll(items);
        c.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(T t) {
                return t == null ? "" : display.apply(t);
            }

            @Override
            public T fromString(String s) {
                for (T t : c.getItems()) if (display.apply(t).equals(s)) return t;
                return null;
            }
        });
        return c;
    }

    public static void setDanger(Node n, boolean on) {
        n.pseudoClassStateChanged(DANGER, on);
    }

    public static Node emptyState(String icon, String title, String text, Node... actions) {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.setMaxWidth(520);
        FontIcon i = icon(icon, 48);
        i.getStyleClass().add("empty-icon");
        Label t = label(title, "title-3");
        Label d = muted(text);
        d.setAlignment(Pos.CENTER);
        d.setStyle("-fx-text-alignment: center;");
        HBox a = new HBox(8, actions);
        a.setAlignment(Pos.CENTER);
        box.getChildren().addAll(i, t, d, a);
        StackPane sp = new StackPane(box);
        sp.getStyleClass().add("empty-state");
        return sp;
    }

    // ------------------------------------------------------------------ images

    /** Nearest-neighbour upscale so pixel art stays sharp. */
    public static Image pixelImage(int[] px, int w, int h, int size) {
        int scale = Math.max(1, size / Math.max(1, Math.max(w, h)));
        WritableImage img = new WritableImage(Math.max(1, w * scale), Math.max(1, h * scale));
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < h * scale; y++) {
            for (int x = 0; x < w * scale; x++) {
                pw.setArgb(x, y, px[(y / scale) * w + (x / scale)]);
            }
        }
        return img;
    }

    // ------------------------------------------------------------------ dialogs

    public static void error(Window owner, String header, Throwable t) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        styleDialog(a, owner);
        a.setTitle(I18n.t("dialog.error"));
        a.setHeaderText(header);
        a.setContentText(t == null ? "" : String.valueOf(t.getMessage()));
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            TextArea ta = new TextArea(sw.toString());
            ta.setEditable(false);
            ta.getStyleClass().add("mono");
            ta.setPrefRowCount(12);
            a.getDialogPane().setExpandableContent(ta);
        }
        a.showAndWait();
    }

    public static void info(Window owner, String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        styleDialog(a, owner);
        a.setTitle("ModStudio");
        a.setHeaderText(header);
        a.setContentText(text);
        a.showAndWait();
    }

    public static boolean confirm(Window owner, String header, String text) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        styleDialog(a, owner);
        a.setTitle("ModStudio");
        a.setHeaderText(header);
        a.setContentText(text);
        ButtonType ok = new ButtonType(I18n.t("action.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        a.getButtonTypes().setAll(ok, cancel);
        return a.showAndWait().orElse(cancel) == ok;
    }

    /** Asks for a single line of text; validator returns an error message or null. */
    public static Optional<String> prompt(Window owner, String title, String label, String initial,
                                          Function<String, String> validator) {
        Dialog<String> d = new Dialog<>();
        styleDialog(d, owner);
        d.setTitle(title);
        d.setHeaderText(title);
        TextField tf = new TextField(initial);
        tf.setPrefColumnCount(28);
        Label err = help("");
        err.getStyleClass().add("error-text");
        VBox box = new VBox(8, label(label), tf, err);
        box.setPadding(new Insets(4, 0, 0, 0));
        d.getDialogPane().setContent(box);
        ButtonType ok = new ButtonType(I18n.t("action.ok"), ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
        Node okButton = d.getDialogPane().lookupButton(ok);
        Runnable check = () -> {
            String msg = validator == null ? null : validator.apply(tf.getText().trim());
            err.setText(msg == null ? "" : msg);
            setDanger(tf, msg != null);
            okButton.setDisable(msg != null);
        };
        tf.textProperty().addListener((o, a, b) -> check.run());
        check.run();
        d.setResultConverter(bt -> bt == ok ? tf.getText().trim() : null);
        javafx.application.Platform.runLater(() -> {
            tf.requestFocus();
            tf.selectAll();
        });
        return d.showAndWait();
    }
}
