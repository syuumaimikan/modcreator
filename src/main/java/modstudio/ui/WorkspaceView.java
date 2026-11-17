package modstudio.ui;

import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Window;
import modstudio.core.*;
import modstudio.i18n.I18n;
import modstudio.ui.pages.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Project workspace: sidebar navigation, page area and status bar. */
public final class WorkspaceView extends BorderPane {
    public final MainWindow main;
    public final Workspace ws;
    private final StackPane content = new StackPane();
    private final Map<String, NavItem> nav = new LinkedHashMap<>();
    private final Map<String, Node> pages = new LinkedHashMap<>();
    private String current = "overview";
    private final Label projectTitle = Ui.label("", "project-title");
    private final Label projectSub = Ui.label("", "text-muted", "text-small");
    private final ImageView projectIcon = new ImageView();
    private final Label statusSaved = new Label();
    private final Label statusProblems = new Label();
    private final ChangeListener<Boolean> dirtyListener;
    private final ListChangeListener<Validator.Problem> problemListener;

    public interface Page {
        default void onShow() {}
    }

    public WorkspaceView(MainWindow main, Workspace ws) {
        this.main = main;
        this.ws = ws;
        getStyleClass().add("workspace");

        setTop(topBar());
        setLeft(sidebar());
        content.getStyleClass().add("page-host");
        setCenter(content);
        setBottom(statusBar());

        dirtyListener = (o, a, b) -> updateStatus();
        ws.dirtyProperty().addListener(dirtyListener);
        problemListener = c -> {
            updateStatus();
            updateCounts();
        };
        ws.problems().addListener(problemListener);
        ws.onStructureChanged(() -> {
            updateCounts();
            updateHeader();
        });
        updateHeader();
        updateCounts();
        updateStatus();
        show("overview");
    }

    public void dispose() {
        if (pages.get("play") instanceof PlayPage pp) pp.dispose();
        ws.dirtyProperty().removeListener(dirtyListener);
        ws.problems().removeListener(problemListener);
    }

    public Window window() {
        return main.stage;
    }

    public String currentPage() {
        return current;
    }

    // ------------------------------------------------------------------ layout

    private Node topBar() {
        projectIcon.setFitWidth(32);
        projectIcon.setFitHeight(32);
        projectIcon.setSmooth(false);
        VBox titles = new VBox(0, projectTitle, projectSub);
        titles.setAlignment(Pos.CENTER_LEFT);

        Button home = Ui.iconButton("mdi2h-home-outline", I18n.t("action.home"), main::closeToHome);
        Button save = Ui.button(I18n.t("action.save"), "mdi2c-content-save-outline", main::save);
        Button play = Ui.button(I18n.t("action.play"), "mdi2p-play", () -> show("play"));
        play.setTooltip(new javafx.scene.control.Tooltip(I18n.t("action.play_tip")));
        Button build = Ui.accent(I18n.t("action.build_export"), "mdi2p-package-variant-closed", () -> show("build"));
        Button settings = Ui.iconButton("mdi2c-cog-outline", I18n.t("action.settings"), main::openSettings);

        HBox bar = Ui.hbox(12, home, projectIcon, titles, Ui.spacer(), save, play, build, settings);
        bar.getStyleClass().add("top-bar");
        bar.setPadding(new Insets(8, 14, 8, 10));
        return bar;
    }

    private Node sidebar() {
        VBox box = new VBox(2);
        box.setPadding(new Insets(8, 8, 16, 8));
        section(box, I18n.t("nav.project"));
        item(box, "overview", "mdi2v-view-dashboard-outline", I18n.t("page.overview"));
        item(box, "textures", "mdi2p-palette-outline", I18n.t("page.textures"));
        item(box, "translations", "mdi2t-translate", I18n.t("page.translations"));
        item(box, "secrets", "mdi2k-key-outline", I18n.t("page.secrets"));
        item(box, "compat", "mdi2s-swap-horizontal", I18n.t("page.compat"));
        section(box, I18n.t("nav.content"));
        for (ElementType t : List.of(ElementType.ITEM, ElementType.BLOCK, ElementType.ARMOR_MATERIAL, ElementType.MODEL, ElementType.SHADER, ElementType.CREATIVE_TAB, ElementType.SOUND)) typeItem(box, t);
        section(box, I18n.t("nav.data"));
        for (ElementType t : List.of(ElementType.RECIPE, ElementType.LOOT_TABLE, ElementType.ADVANCEMENT, ElementType.TAG, ElementType.FUNCTION, ElementType.PREDICATE, ElementType.ITEM_MODIFIER)) typeItem(box, t);
        section(box, I18n.t("nav.gameplay"));
        for (ElementType t : List.of(ElementType.STATUS_EFFECT, ElementType.POTION, ElementType.ENCHANTMENT, ElementType.DAMAGE_TYPE, ElementType.PAINTING, ElementType.SONG)) typeItem(box, t);
        section(box, I18n.t("nav.presentation"));
        for (ElementType t : List.of(ElementType.CUTSCENE, ElementType.DIALOG, ElementType.SCREEN, ElementType.THEME, ElementType.ICON_FONT)) typeItem(box, t);
        section(box, I18n.t("nav.logic"));
        for (ElementType t : List.of(ElementType.PROCEDURE, ElementType.COMMAND, ElementType.KEYBIND, ElementType.CODE_FILE)) typeItem(box, t);
        section(box, I18n.t("nav.world"));
        for (ElementType t : List.of(ElementType.MOB, ElementType.EFFECT, ElementType.STRUCTURE, ElementType.BIOME, ElementType.DIMENSION, ElementType.TRADER)) typeItem(box, t);
        section(box, I18n.t("nav.systems"));
        for (ElementType t : List.of(ElementType.MENU, ElementType.HUD, ElementType.API_REQUEST, ElementType.RESOURCE, ElementType.MACHINE, ElementType.MULTIBLOCK)) typeItem(box, t);
        section(box, I18n.t("nav.output"));
        item(box, "build", "mdi2p-package-variant-closed", I18n.t("page.build"));
        item(box, "play", "mdi2p-play-circle-outline", I18n.t("page.play"));
        item(box, "problems", "mdi2a-alert-circle-outline", I18n.t("page.problems"));

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().addAll("sidebar", "edge-to-edge");
        sp.setPrefWidth(228);
        sp.setMinWidth(200);
        return sp;
    }

    /** Section headers with the element types listed under them, hidden when none of the types fit the project. */
    private final java.util.Map<Label, java.util.List<ElementType>> sectionTypes = new java.util.LinkedHashMap<>();
    private Label currentSection;

    private void section(VBox box, String title) {
        Label l = Ui.label(title.toUpperCase(), "nav-header");
        box.getChildren().add(l);
        currentSection = l;
        sectionTypes.put(l, new java.util.ArrayList<>());
    }

    private void typeItem(VBox box, ElementType t) {
        item(box, t.id, Ui.typeIcon(t), I18n.t("types." + t.id));
        if (currentSection != null) sectionTypes.get(currentSection).add(t);
    }

    /**
     * Shows only the element types the project's targets can export (and types it already has), so a data pack or
     * resource pack project is not offered blocks, machines or Java code.
     */
    public void updateNav() {
        for (var e : sectionTypes.entrySet()) {
            boolean any = false;
            for (ElementType t : e.getValue()) {
                boolean on = modstudio.core.ProjectScope.available(ws.project, t);
                NavItem n = nav.get(t.id);
                if (n != null) {
                    n.setVisible(on);
                    n.setManaged(on);
                }
                any |= on;
            }
            if (e.getValue().isEmpty()) continue;
            e.getKey().setVisible(any);
            e.getKey().setManaged(any);
        }
        NavItem secrets = nav.get("secrets");
        if (secrets != null) {
            boolean on = modstudio.core.ProjectScope.available(ws.project, ElementType.API_REQUEST);
            secrets.setVisible(on);
            secrets.setManaged(on);
        }
    }

    private void item(VBox box, String id, String icon, String text) {
        NavItem n = new NavItem(id, icon, text);
        nav.put(id, n);
        box.getChildren().add(n);
    }

    private final class NavItem extends HBox {
        final Label badge = Ui.label("", "nav-badge");

        NavItem(String id, String icon, String text) {
            super(10);
            setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("nav-item");
            Label l = new Label(text);
            l.getStyleClass().add("nav-text");
            badge.setVisible(false);
            badge.setManaged(false);
            getChildren().addAll(Ui.icon(icon), l, Ui.spacer(), badge);
            setOnMouseClicked(e -> show(id));
        }

        void setBadge(String s, String style) {
            boolean on = s != null && !s.isEmpty();
            badge.setText(on ? s : "");
            badge.setVisible(on);
            badge.setManaged(on);
            badge.getStyleClass().removeAll("badge-danger", "badge-warning");
            if (style != null) badge.getStyleClass().add(style);
        }
    }

    private Node statusBar() {
        Label path = Ui.label(ws.file().toString(), "text-muted");
        HBox bar = Ui.hbox(16, statusSaved, statusProblems, Ui.spacer(), path);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(4, 12, 4, 12));
        statusProblems.setOnMouseClicked(e -> show("problems"));
        return bar;
    }

    // ------------------------------------------------------------------ state

    public void updateHeader() {
        Project p = ws.project;
        projectTitle.setText(p.meta.name);
        List<String> vs = p.mcVersions().stream().map(v -> v.id).toList();
        StringBuilder sb = new StringBuilder("Minecraft " + String.join(", ", vs) + "  |  ");
        List<Target> ts = p.targets();
        for (int i = 0; i < ts.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(I18n.t("target." + ts.get(i).id));
        }
        projectSub.setText(sb.toString());
        projectIcon.setImage(ws.texture(p.meta.icon, 32));
        projectIcon.setVisible(projectIcon.getImage() != null);
        projectIcon.setManaged(projectIcon.getImage() != null);
    }

    private void updateCounts() {
        updateNav();
        for (ElementType t : ElementType.values()) {
            NavItem n = nav.get(t.id);
            if (n == null) continue;
            int c = ws.project.ofType(t).size();
            n.setBadge(c == 0 ? "" : String.valueOf(c), null);
        }
        n("textures").setBadge(ws.project.textures.isEmpty() ? "" : String.valueOf(ws.project.textures.size()), null);
        long err = Validator.count(ws.problems(), Validator.Severity.ERROR);
        long warn = Validator.count(ws.problems(), Validator.Severity.WARNING);
        if (err > 0) n("problems").setBadge(String.valueOf(err), "badge-danger");
        else if (warn > 0) n("problems").setBadge(String.valueOf(warn), "badge-warning");
        else n("problems").setBadge("", null);
    }

    private NavItem n(String id) {
        return nav.get(id);
    }

    private void updateStatus() {
        boolean dirty = ws.dirtyProperty().get();
        statusSaved.setText(dirty ? I18n.t("status.unsaved") : I18n.t("status.saved"));
        statusSaved.setGraphic(Ui.icon(dirty ? "mdi2c-circle-edit-outline" : "mdi2c-check-circle-outline"));
        long err = Validator.count(ws.problems(), Validator.Severity.ERROR);
        long warn = Validator.count(ws.problems(), Validator.Severity.WARNING);
        statusProblems.setText(I18n.t("status.problems", err, warn));
        statusProblems.setGraphic(Ui.icon(err > 0 ? "mdi2a-alert-circle-outline" : warn > 0 ? "mdi2a-alert-outline" : "mdi2c-check-circle-outline"));
        statusProblems.getStyleClass().removeAll("status-error", "status-warning");
        if (err > 0) statusProblems.getStyleClass().add("status-error");
        else if (warn > 0) statusProblems.getStyleClass().add("status-warning");
    }

    // ------------------------------------------------------------------ navigation

    public void show(String id) {
        if (!nav.containsKey(id)) id = "overview";
        current = id;
        nav.forEach((k, v) -> {
            if (k.equals(current)) {
                if (!v.getStyleClass().contains("selected")) v.getStyleClass().add("selected");
            } else v.getStyleClass().remove("selected");
        });
        Node page = pages.computeIfAbsent(id, this::createPage);
        content.getChildren().setAll(page);
        if (page instanceof Page p) p.onShow();
    }

    private Node createPage(String id) {
        return switch (id) {
            case "overview" -> new OverviewPage(this);
            case "textures" -> new TexturesPage(this);
            case "translations" -> new TranslationsPage(this);
            case "build" -> new BuildPage(this);
            case "play" -> new PlayPage(this);
            case "problems" -> new ProblemsPage(this);
            case "secrets" -> new SecretsPage(this);
            case "compat" -> new CompatPage(this);
            default -> new ElementsPage(this, ElementType.of(id));
        };
    }

    public void openElement(String uid) {
        Element e = ws.project.byUid(uid);
        if (e == null) return;
        show(e.type().id);
        if (pages.get(e.type().id) instanceof ElementsPage ep) ep.select(e);
    }

    /** Drops cached pages that show data affected by global changes (mod id, version, textures). */
    public void invalidatePages(String... ids) {
        for (String id : ids) {
            if (!id.equals(current)) pages.remove(id);
        }
    }

    public void invalidateAllExceptCurrent() {
        pages.keySet().removeIf(k -> !k.equals(current));
    }
}
