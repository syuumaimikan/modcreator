package modstudio.ui.controls;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import modstudio.core.Elements.Step;
import modstudio.core.Logic;
import modstudio.core.Script;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;

import java.util.List;
import java.util.function.Supplier;

/**
 * Edits one list of procedure steps in three interchangeable views: blocks (cards), a node graph and
 * text script. All views work on the same step tree, so a feature can be started visually and finished as code.
 */
public final class LogicEditor extends VBox {
    private static String lastMode = "blocks";

    private final IdField.Env env;
    private final List<Step> steps;
    private final Runnable onChange;
    private final Supplier<String> start;
    private final StackPane body = new StackPane();
    private final Label status = Ui.muted("");
    private final ToggleGroup modes = new ToggleGroup();
    private CodeEditor code;
    private boolean scriptValid = true;

    public LogicEditor(IdField.Env env, List<Step> steps, Runnable onChange, Supplier<String> start) {
        super(8);
        this.env = env;
        this.steps = steps;
        this.onChange = onChange;
        this.start = start;
        getStyleClass().add("logic-editor");
        HBox bar = new HBox(0);
        bar.getStyleClass().add("segmented");
        for (String m : List.of("blocks", "graph", "script")) {
            ToggleButton b = new ToggleButton(I18n.t("logic.mode." + m));
            b.setGraphic(Ui.icon(switch (m) {
                case "blocks" -> "mdi2v-view-agenda-outline";
                case "graph" -> "mdi2g-graph-outline";
                default -> "mdi2c-code-braces";
            }));
            b.setUserData(m);
            b.setToggleGroup(modes);
            b.getStyleClass().add(m.equals("blocks") ? "left-pill" : m.equals("script") ? "right-pill" : "center-pill");
            bar.getChildren().add(b);
        }
        modes.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                modes.selectToggle(a);
                return;
            }
            show((String) b.getUserData());
        });
        HBox top = Ui.hbox(8, bar, Ui.spacer(), status);
        getChildren().addAll(top, body);
        for (Toggle t : modes.getToggles()) if (lastMode.equals(t.getUserData())) modes.selectToggle(t);
        if (modes.getSelectedToggle() == null) modes.selectToggle(modes.getToggles().get(0));
    }

    /** View used by the next logic editor that opens ("blocks", "graph" or "script"). */
    public static void setDefaultMode(String mode) {
        lastMode = mode;
    }

    /** Switches this editor to a view ("blocks", "graph" or "script"). */
    public void setMode(String mode) {
        for (Toggle t : modes.getToggles()) if (mode.equals(t.getUserData())) modes.selectToggle(t);
    }

    private void show(String mode) {
        if (!scriptValid && !mode.equals("script")) {
            // keep the last valid steps; the broken text is discarded when leaving the script view
            scriptValid = true;
        }
        lastMode = mode;
        status.setText("");
        Ui.setDanger(status, false);
        Node view = switch (mode) {
            case "graph" -> new FlowGraph(env, steps, onChange, start);
            case "script" -> scriptView();
            default -> new StepsEditor(env, steps, onChange, 0);
        };
        body.getChildren().setAll(view);
    }

    private Node scriptView() {
        code = new CodeEditor("script");
        code.setPrefHeight(420);
        code.setMinHeight(260);
        code.setText(Script.print(steps));
        code.setOnTextChanged(this::parse);
        MenuButton insert = new MenuButton(I18n.t("logic.insert"));
        insert.setGraphic(Ui.icon("mdi2p-plus"));
        insert.getItems().addAll(StepsEditor.stepMenus(kind -> {
            Step s = Logic.newStep(kind);
            String line = Script.print(List.of(s));
            code.area().insertText(code.area().getCaretPosition(), line);
            code.area().requestFocus();
        }));
        Button format = Ui.button(I18n.t("logic.format"), "mdi2f-format-align-left", () -> {
            if (scriptValid) code.setText(Script.print(steps));
        });
        Label help = Ui.help(I18n.t("logic.script_help"));
        HBox tools = Ui.hbox(8, insert, format);
        BorderPane p = new BorderPane(code);
        p.setTop(new VBox(6, help, tools));
        BorderPane.setMargin(code, new Insets(6, 0, 0, 0));
        showOk();
        return p;
    }

    private void parse(String text) {
        try {
            List<Step> parsed = Script.parse(text);
            steps.clear();
            steps.addAll(parsed);
            scriptValid = true;
            showOk();
            onChange.run();
        } catch (Script.ParseException e) {
            scriptValid = false;
            status.setText(I18n.t("logic.error", e.line, e.column, e.getMessage()));
            Ui.setDanger(status, true);
        }
    }

    private void showOk() {
        status.setText(I18n.t("logic.ok", count(steps)));
        Ui.setDanger(status, false);
    }

    private static int count(List<Step> list) {
        int n = 0;
        for (Step s : list) n += 1 + count(s.then) + count(s.orElse);
        return n;
    }
}
