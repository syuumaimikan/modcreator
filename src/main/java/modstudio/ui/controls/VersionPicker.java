package modstudio.ui.controls;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import modstudio.core.McVersion;
import modstudio.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Check boxes for the Minecraft versions a project is exported for. At least one stays selected. */
public final class VersionPicker extends FlowPane {
    private final Map<McVersion, CheckBox> boxes = new LinkedHashMap<>();
    private final ReadOnlyObjectWrapper<List<McVersion>> selected = new ReadOnlyObjectWrapper<>(List.of());
    private boolean updating;

    public VersionPicker(Collection<McVersion> initial) {
        super(14, 6);
        for (McVersion v : McVersion.values()) {
            CheckBox cb = new CheckBox(v.id);
            cb.setSelected(initial.contains(v));
            cb.setTooltip(new Tooltip(I18n.t("version.tooltip", v.id, v.java, v.dataFormat, v.resourceFormat)));
            cb.selectedProperty().addListener((o, a, b) -> {
                if (updating) return;
                if (!b && current().isEmpty()) {
                    updating = true;
                    cb.setSelected(true);
                    updating = false;
                    return;
                }
                selected.set(current());
            });
            boxes.put(v, cb);
            getChildren().add(cb);
        }
        if (current().isEmpty()) boxes.get(McVersion.V1_21_1).setSelected(true);
        selected.set(current());
    }

    private List<McVersion> current() {
        List<McVersion> r = new ArrayList<>();
        boxes.forEach((v, cb) -> {
            if (cb.isSelected()) r.add(v);
        });
        return r;
    }

    /** Selected versions, oldest first. */
    public ReadOnlyObjectProperty<List<McVersion>> selectedProperty() {
        return selected.getReadOnlyProperty();
    }

    public List<McVersion> selected() {
        return selected.get();
    }

    public List<String> ids() {
        List<String> r = new ArrayList<>();
        for (McVersion v : selected()) r.add(v.id);
        return r;
    }
}
