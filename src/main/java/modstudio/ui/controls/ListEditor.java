package modstudio.ui.controls;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Editable list of sub objects (boss phases, menu slots, spawns, ...); each entry is edited in its own small form. */
public final class ListEditor<T> extends VBox {
    private final List<T> list;
    private final Supplier<T> factory;
    private final BiFunction<T, Integer, String> title;
    private final BiConsumer<T, Form> fill;
    private final Runnable onChange;
    private final String addLabel;

    public ListEditor(List<T> list, Supplier<T> factory, BiFunction<T, Integer, String> title, BiConsumer<T, Form> fill,
                      Runnable onChange, String addLabel) {
        super(8);
        this.list = list;
        this.factory = factory;
        this.title = title;
        this.fill = fill;
        this.onChange = onChange;
        this.addLabel = addLabel;
        getStyleClass().add("list-editor");
        rebuild();
    }

    public void rebuild() {
        getChildren().clear();
        if (list.isEmpty()) getChildren().add(Ui.help(I18n.t("list.empty")));
        for (int i = 0; i < list.size(); i++) getChildren().add(card(list.get(i), i));
        Button add = Ui.button(addLabel, "mdi2p-plus", () -> {
            list.add(factory.get());
            onChange.run();
            rebuild();
        });
        getChildren().add(add);
    }

    private VBox card(T item, int index) {
        Label t = Ui.label(title.apply(item, index), "step-title");
        Button up = Ui.iconButton("mdi2c-chevron-up", I18n.t("action.move_up"), () -> move(index, -1));
        Button down = Ui.iconButton("mdi2c-chevron-down", I18n.t("action.move_down"), () -> move(index, 1));
        Button del = Ui.iconButton("mdi2c-close", I18n.t("action.delete"), () -> {
            list.remove(index);
            onChange.run();
            rebuild();
        });
        up.setDisable(index == 0);
        down.setDisable(index == list.size() - 1);
        HBox head = Ui.hbox(4, t, Ui.spacer(), up, down, del);
        Form form = Form.inline(() -> {
            t.setText(title.apply(item, index));
            onChange.run();
        });
        fill.accept(item, form);
        form.updateVisibility();
        VBox card = new VBox(8, head, form);
        card.getStyleClass().addAll("step-card", "step-data");
        card.setPadding(new Insets(8, 10, 10, 12));
        return card;
    }

    private void move(int i, int d) {
        int j = i + d;
        if (j < 0 || j >= list.size()) return;
        Collections.swap(list, i, j);
        onChange.run();
        rebuild();
    }
}
