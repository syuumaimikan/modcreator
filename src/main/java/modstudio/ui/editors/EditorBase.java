package modstudio.ui.editors;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import modstudio.core.*;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.IdField;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared header, name handling and localisation rows for element editors. */
abstract class EditorBase<T extends Element> {
    protected final Ctx c;
    protected final T e;
    protected final Form f;
    private final Label idLabel = Ui.label("", "text-muted", "mono-small");

    EditorBase(Ctx c, T e) {
        this.c = c;
        this.e = e;
        this.f = new Form(this::onChange);
    }

    protected static String t(String key, Object... args) {
        return I18n.t(key, args);
    }

    protected IdField.Env env() {
        return new IdField.Env(c.ws, c.window());
    }

    private void onChange() {
        c.changed();
        c.listChanged();
        refresh();
    }

    /** Called after every change; editors update previews here. */
    protected void refresh() {}

    protected abstract void fill();

    Node build() {
        f.addTop(header());
        fill();
        f.updateVisibility();
        refresh();
        return Ui.scroll(f);
    }

    /** Full resource id of this element as used in commands and other files. */
    protected String fullId() {
        return c.ns() + ":" + e.name;
    }

    private Node header() {
        Label type = Ui.label(t("type." + e.type().id), "text-muted", "text-small");
        Label title = Ui.label("", "title-3");
        title.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> e.name, c.ws.revisionProperty()));
        idLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(this::idText, c.ws.revisionProperty()));
        Button copy = Ui.iconButton("mdi2c-content-copy", t("editor.copy_id"), () -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(idText());
            Clipboard.getSystemClipboard().setContent(cc);
        });
        HBox idRow = Ui.hbox(4, idLabel, copy);
        VBox box = new VBox(2, Ui.hbox(8, Ui.icon(Ui.typeIcon(e.type()), 20), type), title, idRow);
        box.setPadding(new Insets(0, 0, 0, 2));
        return box;
    }

    protected String idText() {
        return switch (e.type()) {
            case COMMAND -> "/" + e.name;
            case TAG -> "#" + modstudio.ui.controls.Catalog.tagNs((Elements.Tag) e, c.ns()) + ":" + e.name;
            case PROCEDURE -> c.ns() + ":procedures/" + e.name;
            case CODE_FILE -> ((Elements.CodeFile) e).path;
            case CREATIVE_TAB -> c.ns() + ":" + e.name;
            default -> fullId();
        };
    }

    // ------------------------------------------------------------------ name

    /** Name row with validation; renaming updates references across the project. */
    protected void nameRow() {
        TextField tf = new TextField(e.name);
        Label err = Ui.errorLabel();
        Runnable commit = () -> {
            String n = tf.getText().trim();
            if (n.equals(e.name)) {
                err.setText("");
                Ui.setDanger(tf, false);
                return;
            }
            String msg = nameError(n);
            if (msg != null) {
                err.setText(msg);
                Ui.setDanger(tf, true);
                return;
            }
            err.setText("");
            Ui.setDanger(tf, false);
            rename(c.p, e, n);
            c.ws.structureChanged();
            onChange();
        };
        tf.textProperty().addListener((o, a, b) -> {
            String msg = b.trim().equals(e.name) ? null : nameError(b.trim());
            Ui.setDanger(tf, msg != null);
            err.setText(msg == null ? "" : msg);
        });
        tf.setOnAction(x -> commit.run());
        tf.focusedProperty().addListener((o, a, b) -> {
            if (!b) commit.run();
        });
        VBox box = new VBox(4, tf, err);
        String help = e.type() == ElementType.CODE_FILE ? t("help.name_codefile")
                : e.type().allowsPath() ? t("help.name_path") : t("help.name", c.ns());
        f.row(t("field.name"), box, help);
    }

    private String nameError(String n) {
        ElementType type = e.type();
        if (n.isEmpty()) return t("problem.name_empty", t("type." + type.id));
        if (type != ElementType.CODE_FILE && !(type.allowsPath() ? Ids.PATH : Ids.NAME).matcher(n).matches())
            return t("problem.name_invalid", n);
        for (Element o : c.p.elements) {
            if (o != e && o.type().registryGroup().equals(type.registryGroup()) && o.name.equals(n))
                return t("problem.name_duplicate", n);
        }
        return null;
    }

    static void rename(Project p, Element e, String n) {
        String old = e.name;
        e.name = n;
        String ns = p.meta.modId;
        switch (e.type()) {
            case ITEM, BLOCK, FUNCTION, LOOT_TABLE, ADVANCEMENT, SOUND, STATUS_EFFECT, ENCHANTMENT, DAMAGE_TYPE -> Refactor.renameId(p, ns + ":" + old, ns + ":" + n);
            case TAG -> {
                String tns = modstudio.ui.controls.Catalog.tagNs((Elements.Tag) e, ns);
                Refactor.renameId(p, tns + ":" + old, tns + ":" + n);
            }
            case PROCEDURE -> Refactor.renameId(p, ns + ":procedures/" + old, ns + ":procedures/" + n);
            case CREATIVE_TAB -> Refactor.renameId(p, "tab:" + old, "tab:" + n);
            case MODEL -> {
                for (Elements.Block b : p.all(Elements.Block.class)) if (old.equals(b.model)) b.model = n;
                for (Elements.Item it : p.all(Elements.Item.class)) if (old.equals(it.model)) it.model = n;
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ localisation

    /** One text field per extra game language for a translatable field. */
    protected void localized(String field, String label, Supplier<String> english) {
        if (c.p.meta.languages.isEmpty()) return;
        VBox box = new VBox(6);
        for (String lang : c.p.meta.languages) {
            TextField tf = new TextField(e.loc(field, lang));
            tf.setPromptText(english.get());
            tf.textProperty().addListener((o, a, b) -> {
                e.setLoc(field, lang, b);
                f.changed();
            });
            Label code = Ui.label(lang, "lang-code");
            code.setMinWidth(52);
            HBox row = Ui.hbox(8, code, tf);
            javafx.scene.layout.HBox.setHgrow(tf, javafx.scene.layout.Priority.ALWAYS);
            box.getChildren().add(row);
        }
        f.row(t("field.translations_of", label), box, t("help.translations"));
    }

    protected TextField text(String label, Supplier<String> get, Consumer<String> set) {
        return f.text(label, get, set);
    }
}
