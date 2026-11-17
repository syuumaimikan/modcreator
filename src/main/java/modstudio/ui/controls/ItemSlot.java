package modstudio.ui.controls;

import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import modstudio.core.Ids;
import modstudio.i18n.I18n;

import java.util.function.Consumer;

/** An inventory-style slot showing an item; click to choose, right click to clear. */
public final class ItemSlot extends StackPane {
    private final IdField.Env env;
    private final IdField.Kind kind;
    private final Consumer<String> onChange;
    private String value;
    private final int size;

    public ItemSlot(IdField.Env env, IdField.Kind kind, String value, int size, Consumer<String> onChange) {
        this.env = env;
        this.kind = kind;
        this.onChange = onChange;
        this.size = size;
        this.value = value == null ? "" : value;
        getStyleClass().add("slot");
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setAlignment(Pos.CENTER);
        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) pick();
        });
        MenuItem choose = new MenuItem(I18n.t("action.choose"));
        choose.setOnAction(e -> pick());
        MenuItem clear = new MenuItem(I18n.t("action.clear"));
        clear.setOnAction(e -> set(""));
        setOnContextMenuRequested(e -> new ContextMenu(choose, clear).show(this, e.getScreenX(), e.getScreenY()));
        render();
    }

    public String getValue() {
        return value;
    }

    public void set(String v) {
        value = v == null ? "" : v;
        render();
        onChange.accept(value);
    }

    private void pick() {
        Picker.show(env.window(), I18n.t("picker.title"), Catalog.entries(env.ws(), kind), true, value).ifPresent(this::set);
    }

    private void render() {
        getChildren().clear();
        if (value.isEmpty()) {
            Tooltip.install(this, new Tooltip(I18n.t("slot.empty")));
            return;
        }
        Tooltip.install(this, new Tooltip(value));
        Image img = env.ws().itemImage(value, size - 12);
        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(size - 12);
            iv.setFitHeight(size - 12);
            iv.setSmooth(false);
            getChildren().add(iv);
        } else {
            String text = value.startsWith("#") ? "#" + Ids.path(value) : Ids.path(value);
            Label l = new Label(text.replace('_', ' '));
            l.getStyleClass().add("slot-text");
            l.setWrapText(true);
            l.setMaxWidth(size - 6);
            l.setAlignment(Pos.CENTER);
            l.setStyle("-fx-text-alignment: center;");
            getChildren().add(l);
        }
    }
}
