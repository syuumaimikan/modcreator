package modstudio.ui.controls;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import modstudio.core.Ids;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.Workspace;

import java.util.function.Consumer;

/** Text field for a resource id with a search picker. */
public final class IdField extends HBox {
    public enum Kind { ITEM, ITEM_OR_TAG, BLOCK, BLOCK_OR_TAG, ENTITY, EFFECT, SOUND, PARTICLE, FUNCTION, DIMENSION, ADVANCEMENT, LOOT_TABLE, RECIPE, ENCHANTMENT, DAMAGE_TYPE, POTION, ANY }

    public record Env(Workspace ws, Window window) {}

    private final TextField field = new TextField();
    private final ImageView preview = new ImageView();
    private final Env env;
    private Kind kind;
    private boolean optional = true;

    public IdField(Env env, Kind kind, String value, Consumer<String> onChange) {
        super(6);
        this.env = env;
        this.kind = kind;
        setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        field.setText(value == null ? "" : value);
        field.setPromptText("namespace:id");
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(420);
        preview.setFitWidth(24);
        preview.setFitHeight(24);
        preview.setSmooth(false);
        Button pick = Ui.iconButton("mdi2m-magnify", I18n.t("action.choose"), this::pick);
        pick.getStyleClass().remove("flat");
        field.textProperty().addListener((o, a, b) -> {
            validate();
            updatePreview();
            onChange.accept(b.trim());
        });
        field.focusedProperty().addListener((o, a, b) -> {
            if (!b) normalize();
        });
        field.setOnAction(e -> normalize());
        getChildren().addAll(preview, field, pick);
        validate();
        updatePreview();
    }

    public IdField required() {
        optional = false;
        validate();
        return this;
    }

    public void setKind(Kind k) {
        this.kind = k;
    }

    public String getValue() {
        return field.getText().trim();
    }

    public void setValue(String v) {
        field.setText(v == null ? "" : v);
    }

    public TextField field() {
        return field;
    }

    private void normalize() {
        String s = field.getText().trim();
        if (!s.isEmpty() && !s.contains(":") && kind != Kind.ANY) {
            String full = Ids.full(s);
            if (!full.equals(s)) field.setText(full);
        }
    }

    private void validate() {
        String s = field.getText().trim();
        boolean bad = s.isEmpty() ? !optional : !(s.contains(":") ? Ids.RESOURCE.matcher(s).matches() : Ids.PATH.matcher(s.replace("#", "")).matches());
        Ui.setDanger(field, bad);
    }

    private void updatePreview() {
        var img = env.ws().itemImage(field.getText().trim(), 24);
        preview.setImage(img);
        preview.setVisible(img != null);
        preview.setManaged(img != null);
    }

    private void pick() {
        Picker.show(env.window(), I18n.t("picker.title"), Catalog.entries(env.ws(), kind), true, getValue())
                .ifPresent(this::setValue);
    }
}
