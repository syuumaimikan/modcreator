package modstudio.ui.controls;

import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import modstudio.core.Ids;
import modstudio.core.Images;
import modstudio.core.TextureAsset;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.Workspace;
import javafx.stage.Window;

import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Chooses one of the project textures; can create, import and edit textures in place. */
public final class TextureField extends HBox {
    private final Workspace ws;
    private final Window owner;
    private final ComboBox<String> combo = new ComboBox<>();
    private final ImageView preview = new ImageView();
    private final Consumer<String> onChange;
    private final Supplier<String> suggestedName;
    private final String template;
    private boolean updating;

    public TextureField(Workspace ws, Window owner, String value, Supplier<String> suggestedName, String template, Consumer<String> onChange) {
        super(6);
        this.ws = ws;
        this.owner = owner;
        this.onChange = onChange;
        this.suggestedName = suggestedName;
        this.template = template;
        setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        preview.setFitWidth(32);
        preview.setFitHeight(32);
        preview.setSmooth(false);
        javafx.scene.layout.StackPane frame = new javafx.scene.layout.StackPane(preview);
        frame.getStyleClass().add("preview-frame");
        frame.setMinSize(40, 40);

        combo.setPrefWidth(220);
        combo.setCellFactory(lv -> new TexCell());
        combo.setButtonCell(new TexCell());
        reloadItems();
        combo.setValue(value == null || value.isEmpty() ? null : value);
        combo.valueProperty().addListener((o, a, b) -> {
            updatePreview();
            if (!updating) onChange.accept(b == null ? "" : b);
        });

        Button create = Ui.iconButton("mdi2p-plus", I18n.t("texture.new"), this::createNew);
        Button edit = Ui.iconButton("mdi2p-pencil", I18n.t("texture.edit"), this::editCurrent);
        Button imp = Ui.iconButton("mdi2i-import", I18n.t("texture.import"), this::importPng);
        edit.disableProperty().bind(combo.valueProperty().isNull());
        getChildren().addAll(frame, combo, create, edit, imp);
        updatePreview();
    }

    private void reloadItems() {
        updating = true;
        String v = combo.getValue();
        combo.getItems().setAll(ws.project.textures.stream().map(t -> t.id).toList());
        combo.setValue(v);
        updating = false;
    }

    private void updatePreview() {
        preview.setImage(combo.getValue() == null ? null : ws.texture(combo.getValue(), 32));
    }

    private String nameCheck(String n, TextureAsset self) {
        if (!Ids.NAME.matcher(n).matches()) return I18n.t("problem.texture_name_invalid", n);
        TextureAsset other = ws.project.textureByName(n);
        if (other != null && other != self) return I18n.t("problem.texture_name_duplicate", n);
        return null;
    }

    private void createNew() {
        TextureAsset t = new TextureAsset();
        t.name = ws.project.uniqueTextureName(suggestedName.get());
        int[] size = modstudio.core.TextureTemplates.size(template);
        t.width = size[0];
        t.height = size[1];
        if (TextureEditor.edit(owner, t, template, n -> nameCheck(n, t))) {
            ws.project.textures.add(t);
            ws.structureChanged();
            reloadItems();
            combo.setValue(t.id);
        }
    }

    private void editCurrent() {
        TextureAsset t = ws.project.texture(combo.getValue());
        if (t == null) return;
        TextureAsset copy = new TextureAsset();
        copy.id = t.id;
        copy.name = t.name;
        copy.width = t.width;
        copy.height = t.height;
        copy.png = t.png;
        if (TextureEditor.edit(owner, copy, template, n -> nameCheck(n, t))) {
            t.name = copy.name;
            t.width = copy.width;
            t.height = copy.height;
            t.png = copy.png;
            ws.structureChanged();
            reloadItems();
            updatePreview();
            onChange.accept(t.id);
        }
    }

    private void importPng() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        var f = fc.showOpenDialog(owner);
        if (f == null) return;
        try {
            int[] wh = new int[2];
            int[] px = Images.read(Files.readAllBytes(f.toPath()), wh);
            String base = f.getName().replaceFirst("(?i)\\.png$", "");
            TextureAsset t = TextureAsset.of(ws.project.uniqueTextureName(base), px, wh[0], wh[1]);
            ws.project.textures.add(t);
            ws.structureChanged();
            reloadItems();
            combo.setValue(t.id);
        } catch (Exception ex) {
            Ui.error(owner, I18n.t("texture.import_failed"), ex);
        }
    }

    private final class TexCell extends ListCell<String> {
        @Override
        protected void updateItem(String id, boolean empty) {
            super.updateItem(id, empty);
            if (empty || id == null) {
                setText(empty ? null : I18n.t("texture.none"));
                setGraphic(null);
                return;
            }
            TextureAsset t = ws.project.texture(id);
            setText(t == null ? I18n.t("texture.missing") : t.name);
            if (t != null) {
                ImageView iv = new ImageView(ws.texture(id, 16));
                iv.setFitWidth(16);
                iv.setFitHeight(16);
                iv.setSmooth(false);
                setGraphic(iv);
            } else setGraphic(null);
        }
    }
}
