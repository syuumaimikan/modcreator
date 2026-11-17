package modstudio.ui.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import modstudio.core.*;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;
import modstudio.ui.controls.TextureEditor;

import java.nio.file.Files;
import java.util.List;

/** Gallery of project textures with the pixel editor. */
public final class TexturesPage extends BorderPane implements WorkspaceView.Page {
    private final WorkspaceView view;
    private final Project p;
    private final FlowPane grid = new FlowPane(12, 12);
    private final TextField search = new TextField();
    private TextureAsset selected;

    public TexturesPage(WorkspaceView view) {
        this.view = view;
        this.p = view.ws.project;

        Button create = Ui.accent(t("texture.new"), "mdi2p-plus", this::create);
        Button imp = Ui.button(t("texture.import"), "mdi2i-import", this::importFiles);
        Button exportAll = Ui.button(t("texture.export_all"), "mdi2e-export-variant", this::exportAll);
        search.setPromptText(t("action.search"));
        search.textProperty().addListener((o, a, b) -> reload());
        search.setPrefWidth(220);
        VBox head = new VBox(4, Ui.label(t("page.textures"), "title-2"), Ui.muted(t("textures.subtitle")));
        for (Button b : new Button[]{imp, exportAll, create}) b.setMinWidth(Region.USE_PREF_SIZE);
        search.setMinWidth(140);
        head.setMinWidth(0);
        HBox.setHgrow(head, Priority.ALWAYS);
        HBox bar = Ui.hbox(8, head, search, imp, exportAll, create);
        bar.setPadding(new Insets(20, 24, 12, 24));
        setTop(bar);

        grid.setPadding(new Insets(8, 24, 24, 24));
        setCenter(Ui.scroll(grid));
        reload();
    }

    private static String t(String k, Object... a) {
        return I18n.t(k, a);
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        grid.getChildren().clear();
        String q = search.getText().trim().toLowerCase();
        for (TextureAsset tex : p.textures) {
            if (!q.isEmpty() && !tex.name.contains(q)) continue;
            grid.getChildren().add(card(tex));
        }
        if (p.textures.isEmpty()) {
            grid.getChildren().add(Ui.emptyState("mdi2p-palette-outline", t("textures.empty_title"), t("textures.empty_text"),
                    Ui.accent(t("texture.new"), "mdi2p-plus", this::create)));
        }
    }

    private int usage(TextureAsset tex) {
        int n = tex.id.equals(p.meta.icon) ? 1 : 0;
        for (Element e : p.elements) {
            if (e instanceof Elements.Item it && it.texture.equals(tex.id)) n++;
            if (e instanceof Elements.Block b && (b.texture.equals(tex.id) || b.textureTop.equals(tex.id)
                    || b.textureBottom.equals(tex.id) || b.textureSide.equals(tex.id))) n++;
            if (e instanceof Elements.Model m && m.textures.containsValue(tex.id)) n++;
        }
        return n;
    }

    private Node card(TextureAsset tex) {
        ImageView iv = new ImageView(view.ws.texture(tex.id, 64));
        iv.setFitWidth(64);
        iv.setFitHeight(64);
        iv.setSmooth(false);
        StackPane img = new StackPane(iv);
        img.getStyleClass().add("texture-thumb");
        img.setPrefSize(96, 96);
        Label name = Ui.label(tex.name, "cell-title");
        name.setMaxWidth(120);
        int u = usage(tex);
        String dims = tex.animated() ? tex.width + "x" + tex.width + "  " + t("textures.frames", tex.frames()) : tex.width + "x" + tex.height;
        Label info = Ui.label(dims + "  |  " + (u == 0 ? t("textures.unused") : t("textures.used", u)), "text-muted", "text-small");
        info.setWrapText(true);
        info.setMaxWidth(124);
        info.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        info.setMinHeight(Region.USE_PREF_SIZE);
        VBox card = new VBox(6, img, name, info);
        card.setAlignment(Pos.TOP_CENTER);
        card.getStyleClass().add("texture-card");
        if (tex == selected) card.getStyleClass().add("selected");
        card.setPadding(new Insets(10));
        card.setPrefWidth(140);

        MenuItem edit = new MenuItem(t("texture.edit"));
        edit.setOnAction(e -> edit(tex));
        MenuItem dup = new MenuItem(t("action.duplicate"));
        dup.setOnAction(e -> {
            TextureAsset c = new TextureAsset();
            c.name = p.uniqueTextureName(tex.name + "_copy");
            c.width = tex.width;
            c.height = tex.height;
            c.png = tex.png;
            c.frameTime = tex.frameTime;
            c.interpolate = tex.interpolate;
            p.textures.add(p.textures.indexOf(tex) + 1, c);
            view.ws.structureChanged();
            reload();
        });
        MenuItem export = new MenuItem(t("texture.export"));
        export.setOnAction(e -> export(tex));
        MenuItem del = new MenuItem(t("action.delete"));
        del.setOnAction(e -> delete(tex));
        ContextMenu menu = new ContextMenu(edit, dup, export, new SeparatorMenuItem(), del);
        card.setOnContextMenuRequested(e -> menu.show(card, e.getScreenX(), e.getScreenY()));
        card.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            selected = tex;
            if (e.getClickCount() == 2) edit(tex);
            else reload();
        });
        return card;
    }

    private String nameCheck(String n, TextureAsset self) {
        if (!Ids.NAME.matcher(n).matches()) return t("problem.texture_name_invalid", n);
        TextureAsset other = p.textureByName(n);
        if (other != null && other != self) return t("problem.texture_name_duplicate", n);
        return null;
    }

    private void create() {
        TextureAsset tex = new TextureAsset();
        tex.name = p.uniqueTextureName("texture");
        if (TextureEditor.edit(view.window(), tex, "gem", n -> nameCheck(n, tex))) {
            p.textures.add(tex);
            view.ws.structureChanged();
            selected = tex;
            reload();
        }
    }

    private void edit(TextureAsset tex) {
        TextureAsset copy = new TextureAsset();
        copy.id = tex.id;
        copy.name = tex.name;
        copy.width = tex.width;
        copy.height = tex.height;
        copy.png = tex.png;
        copy.frameTime = tex.frameTime;
        copy.interpolate = tex.interpolate;
        if (TextureEditor.edit(view.window(), copy, "gem", n -> nameCheck(n, tex))) {
            tex.name = copy.name;
            tex.width = copy.width;
            tex.height = copy.height;
            tex.png = copy.png;
            tex.frameTime = copy.frameTime;
            tex.interpolate = copy.interpolate;
            view.ws.structureChanged();
            view.updateHeader();
            view.invalidatePages("item", "block");
            reload();
        }
    }

    private void delete(TextureAsset tex) {
        int u = usage(tex);
        String text = u > 0 ? t("textures.delete_used", u) : t("elements.delete_text");
        if (!Ui.confirm(view.window(), t("elements.delete_title", tex.name), text)) return;
        p.textures.remove(tex);
        view.ws.structureChanged();
        reload();
    }

    private void importFiles() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        List<java.io.File> files = fc.showOpenMultipleDialog(view.window());
        if (files == null) return;
        for (var f : files) {
            try {
                int[] wh = new int[2];
                int[] px = Images.read(Files.readAllBytes(f.toPath()), wh);
                TextureAsset tex = TextureAsset.of(p.uniqueTextureName(f.getName().replaceFirst("(?i)\\.png$", "")), px, wh[0], wh[1]);
                if (wh[1] > wh[0] && wh[1] % wh[0] == 0) tex.frameTime = 2;
                p.textures.add(tex);
            } catch (Exception ex) {
                Ui.error(view.window(), t("texture.import_failed") + ": " + f.getName(), ex);
            }
        }
        view.ws.structureChanged();
        reload();
    }

    private void export(TextureAsset tex) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        fc.setInitialFileName(tex.name + ".png");
        var f = fc.showSaveDialog(view.window());
        if (f == null) return;
        try {
            Files.write(f.toPath(), tex.bytes());
        } catch (Exception ex) {
            Ui.error(view.window(), t("texture.export_failed"), ex);
        }
    }

    private void exportAll() {
        DirectoryChooser dc = new DirectoryChooser();
        var dir = dc.showDialog(view.window());
        if (dir == null) return;
        try {
            for (TextureAsset tex : p.textures) Files.write(dir.toPath().resolve(tex.name + ".png"), tex.bytes());
        } catch (Exception ex) {
            Ui.error(view.window(), t("texture.export_failed"), ex);
        }
    }
}
