package modstudio.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import modstudio.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

/**
 * Builds editor forms out of cards and labelled rows. Every field reads its value
 * from a getter, writes through a setter and then calls the change callback.
 */
public final class Form extends VBox {
    private final Runnable onChange;
    private final List<Runnable> visibility = new ArrayList<>();
    private VBox section;
    private VBox sectionCard;
    private Node lastRow;
    private boolean plain;

    /** A form without outer padding or cards, for use inside list items and dialogs. */
    public static Form inline(Runnable onChange) {
        Form f = new Form(onChange);
        f.plain = true;
        f.setPadding(Insets.EMPTY);
        f.setSpacing(8);
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    public Form(Runnable onChange) {
        this.onChange = onChange;
        setSpacing(16);
        setPadding(new Insets(20, 24, 32, 24));
        getStyleClass().add("form");
        setMaxWidth(980);
    }

    /** Runs the change callback and refreshes conditional rows. */
    public void changed() {
        onChange.run();
        updateVisibility();
    }

    public void updateVisibility() {
        for (Runnable r : visibility) r.run();
    }

    // ------------------------------------------------------------------ structure

    public VBox section(String title) {
        return section(title, null);
    }

    public VBox section(String title, String description) {
        sectionCard = new VBox(12);
        if (!plain) sectionCard.getStyleClass().add("card");
        if (title != null) sectionCard.getChildren().add(Ui.label(title, "card-title"));
        if (description != null) sectionCard.getChildren().add(Ui.help(description));
        section = new VBox(10);
        sectionCard.getChildren().add(section);
        getChildren().add(sectionCard);
        return sectionCard;
    }

    /** Adds a node spanning the full width of the current section. */
    public <N extends Node> N add(N node) {
        ensureSection();
        section.getChildren().add(node);
        lastRow = node;
        return node;
    }

    /** Adds a node outside any card. */
    public <N extends Node> N addTop(N node) {
        getChildren().add(node);
        return node;
    }

    public Node row(String label, Node control) {
        return row(label, control, null);
    }

    public Node row(String label, Node control, String help) {
        ensureSection();
        Label l = new Label(label == null ? "" : label);
        l.getStyleClass().add("form-label");
        l.setMinWidth(170);
        l.setPrefWidth(170);
        l.setWrapText(true);
        VBox right = new VBox(4, control);
        if (help != null && !help.isBlank()) right.getChildren().add(Ui.help(help));
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox row = new HBox(12, l, right);
        row.setAlignment(Pos.TOP_LEFT);
        l.setPadding(new Insets(6, 0, 0, 0));
        row.getStyleClass().add("form-row");
        section.getChildren().add(row);
        lastRow = row;
        return row;
    }

    private void ensureSection() {
        if (section == null) section(null);
    }

    /** Shows the most recently added row only while the condition holds. */
    public void showIf(BooleanSupplier cond) {
        showIf(lastRow, cond);
    }

    /** Shows the current section card only while the condition holds. */
    public void sectionShowIf(BooleanSupplier cond) {
        showIf(sectionCard, cond);
    }

    public void showIf(Node n, BooleanSupplier cond) {
        Runnable r = () -> {
            boolean v = cond.getAsBoolean();
            n.setVisible(v);
            n.setManaged(v);
        };
        visibility.add(r);
        r.run();
    }

    // ------------------------------------------------------------------ fields

    public TextField text(String label, Supplier<String> get, Consumer<String> set) {
        return text(label, get, set, null);
    }

    public TextField text(String label, Supplier<String> get, Consumer<String> set, String help) {
        TextField tf = new TextField(get.get());
        tf.textProperty().addListener((o, a, b) -> {
            set.accept(b);
            changed();
        });
        row(label, tf, help);
        return tf;
    }

    public TextArea area(String label, Supplier<String> get, Consumer<String> set, int rows, String help) {
        TextArea ta = new TextArea(get.get());
        ta.setPrefRowCount(rows);
        ta.setWrapText(true);
        ta.textProperty().addListener((o, a, b) -> {
            set.accept(b);
            changed();
        });
        row(label, ta, help);
        return ta;
    }

    public Spinner<Integer> integer(String label, int min, int max, IntSupplier get, IntConsumer set) {
        return integer(label, min, max, get, set, null);
    }

    public Spinner<Integer> integer(String label, int min, int max, IntSupplier get, IntConsumer set, String help) {
        Spinner<Integer> sp = intSpinner(min, max, get.getAsInt(), v -> {
            set.accept(v);
            changed();
        });
        row(label, sp, help);
        return sp;
    }

    /** Spinner that commits while typing, not only on Enter. */
    public static Spinner<Integer> intSpinner(int min, int max, int value, IntConsumer onChange) {
        Spinner<Integer> sp = new Spinner<>(min, max, Math.max(min, Math.min(max, value)));
        sp.setEditable(true);
        sp.setPrefWidth(130);
        sp.valueProperty().addListener((o, a, b) -> {
            if (b != null) onChange.accept(b);
        });
        sp.getEditor().textProperty().addListener((o, a, b) -> {
            try {
                int v = Integer.parseInt(b.trim());
                Ui.setDanger(sp.getEditor(), v < min || v > max);
                if (v >= min && v <= max && !Integer.valueOf(v).equals(sp.getValue())) sp.getValueFactory().setValue(v);
            } catch (NumberFormatException ex) {
                Ui.setDanger(sp.getEditor(), !b.isBlank() && !b.equals("-"));
            }
        });
        sp.focusedProperty().addListener((o, a, b) -> {
            if (!b) sp.getEditor().setText(String.valueOf(sp.getValue()));
        });
        return sp;
    }

    public TextField decimal(String label, DoubleSupplier get, DoubleConsumer set) {
        return decimal(label, get, set, null);
    }

    public TextField decimal(String label, DoubleSupplier get, DoubleConsumer set, String help) {
        TextField tf = decimalField(get.getAsDouble(), v -> {
            set.accept(v);
            changed();
        });
        row(label, tf, help);
        return tf;
    }

    public static TextField decimalField(double value, DoubleConsumer onChange) {
        TextField tf = new TextField(fmt(value));
        tf.setPrefColumnCount(8);
        tf.setMaxWidth(130);
        tf.textProperty().addListener((o, a, b) -> {
            try {
                double v = Double.parseDouble(b.trim());
                Ui.setDanger(tf, false);
                onChange.accept(v);
            } catch (NumberFormatException ex) {
                Ui.setDanger(tf, true);
            }
        });
        return tf;
    }

    public static String fmt(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    public CheckBox check(String text, BooleanSupplier get, Consumer<Boolean> set) {
        return check(text, get, set, null);
    }

    public CheckBox check(String text, BooleanSupplier get, Consumer<Boolean> set, String help) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(get.getAsBoolean());
        cb.selectedProperty().addListener((o, a, b) -> {
            set.accept(b);
            changed();
        });
        row("", cb, help);
        return cb;
    }

    public ComboBox<String> choice(String label, List<String> values, Function<String, String> display,
                                   Supplier<String> get, Consumer<String> set) {
        return choice(label, values, display, get, set, null);
    }

    public ComboBox<String> choice(String label, List<String> values, Function<String, String> display,
                                   Supplier<String> get, Consumer<String> set, String help) {
        ComboBox<String> c = Ui.combo(values, display);
        String v = get.get();
        if (v != null && !values.contains(v)) c.getItems().add(v);
        c.setValue(v);
        c.setPrefWidth(260);
        c.valueProperty().addListener((o, a, b) -> {
            if (b == null) return;
            set.accept(b);
            changed();
        });
        row(label, c, help);
        return c;
    }

    public IdField id(String label, IdField.Kind kind, Supplier<String> get, Consumer<String> set, IdField.Env env) {
        return id(label, kind, get, set, env, null);
    }

    public IdField id(String label, IdField.Kind kind, Supplier<String> get, Consumer<String> set, IdField.Env env, String help) {
        IdField f = new IdField(env, kind, get.get(), v -> {
            set.accept(v);
            changed();
        });
        row(label, f, help);
        return f;
    }
}
