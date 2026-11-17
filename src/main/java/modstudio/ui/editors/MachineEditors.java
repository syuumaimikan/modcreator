package modstudio.ui.editors;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import modstudio.core.Element;
import modstudio.core.Ids;
import modstudio.core.Systems.Machine;
import modstudio.core.Systems.MachineIO;
import modstudio.core.Systems.Multiblock;
import modstudio.core.Systems.Resource;
import modstudio.ui.Ui;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.IdField;
import modstudio.ui.controls.ListEditor;
import modstudio.ui.controls.VoxelEditor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Editors for resources, machines and multiblocks. */
final class MachineEditors {
    private MachineEditors() {}

    static final List<String> MODELS = List.of("buffer", "stress");
    static final List<String> MODES = List.of("produce", "consume", "store");
    static final List<String> TRANSFERS = List.of("none", "adjacent", "radius", "beam");
    static final List<String> CELESTIAL = List.of("none", "sky", "day", "night", "moon_full");
    static final List<String> OVERLOAD = List.of("stop", "break", "explode");

    /** Editable combo box listing project elements by name; an empty value means none. */
    static ComboBox<String> ref(Form f, String label, List<String> names, boolean allowNone, Supplier<String> get, Consumer<String> set, String help) {
        ComboBox<String> box = new ComboBox<>();
        List<String> items = new ArrayList<>();
        if (allowNone) items.add("");
        items.addAll(names);
        box.getItems().setAll(items);
        box.setEditable(true);
        box.setValue(get.get());
        box.setPrefWidth(260);
        box.valueProperty().addListener((o, a, b) -> {
            set.accept(b == null ? "" : b.trim());
            f.changed();
        });
        f.row(label, box, help);
        return box;
    }

    static Color color(String hex) {
        try {
            return Color.web(hex.trim().startsWith("#") ? hex.trim() : "#" + hex.trim());
        } catch (Exception e) {
            return Color.web("#F5C542");
        }
    }

    static String css(Color c) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    /** A rounded label in the resource colour with readable text. */
    static Label pill(String text, String hex) {
        Color c = color(hex);
        double lum = 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
        Label l = new Label(text);
        l.setStyle("-fx-background-color: " + css(c) + "; -fx-background-radius: 10; -fx-padding: 3 10 3 10; -fx-text-fill: "
                + (lum > 0.6 ? "#1B1B1B" : "#FFFFFF") + ";");
        return l;
    }

    // ------------------------------------------------------------------ resource

    static final class ResourceEditor extends EditorBase<Resource> {
        private final Label preview = new Label();
        private final HBox swatch = new HBox();

        ResourceEditor(Ctx c, Resource e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.resource"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            f.text(t("field.resource.unit"), () -> e.unit, v -> e.unit = v, t("help.resource.unit"));
            f.text(t("field.resource.color"), () -> e.color, v -> e.color = v, t("help.resource.color"));
            swatch.setSpacing(8);
            swatch.setAlignment(Pos.CENTER_LEFT);
            f.add(swatch);
            f.choice(t("field.resource.model"), MODELS, x -> t("resource.model." + x), () -> e.model, v -> e.model = v, t("help.resource.model"));

            f.section(t("section.resource.pollution"), t("help.resource.pollution"));
            f.check(t("field.resource.pollution"), () -> e.pollution, v -> e.pollution = v);
            f.showIf(() -> e.model.equals("buffer"));
            f.integer(t("field.resource.pollution_limit"), 1, 1_000_000, () -> e.pollutionLimit, v -> e.pollutionLimit = v, t("help.resource.pollution_limit"));
            f.showIf(() -> e.pollution && e.model.equals("buffer"));
            SystemEditors.logic(this, t("section.resource.on_pollution"), t("help.resource.on_pollution"), e.onPollution, () -> t("resource.start.pollution", e.pollutionLimit));
            f.sectionShowIf(() -> e.pollution && e.model.equals("buffer"));

            f.section(t("section.resource.usage"));
            f.add(preview);
            preview.setWrapText(true);
            preview.getStyleClass().add("help");
        }

        @Override
        protected void refresh() {
            String label = e.displayName.isBlank() ? Ids.titleCase(e.name) : e.displayName;
            swatch.getChildren().setAll(pill(label + "  120 / 1000 " + e.unit, e.color), pill(e.unit, e.color));
            preview.setText(t(e.model.equals("stress") ? "help.resource.usage_stress" : "help.resource.usage_buffer", c.ns(), e.name));
        }
    }

    // ------------------------------------------------------------------ machine

    static final class MachineEditor extends EditorBase<Machine> {
        private final HBox flow = new HBox(12);
        private final FlowPane needs = new FlowPane(6, 6);
        private final Label summary = new Label();

        MachineEditor(Ctx c, Machine e) {
            super(c, e);
        }

        private Resource resource(String name) {
            Element el = c.p.byName(modstudio.core.ElementType.RESOURCE, name == null ? "" : name.trim());
            return el instanceof Resource r ? r : null;
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.machine"));
            nameRow();
            VBox diagram = new VBox(10, flow, needs, summary);
            diagram.getStyleClass().add("machine-diagram");
            diagram.setPadding(new Insets(14));
            summary.setWrapText(true);
            summary.getStyleClass().add("text-muted");
            f.add(diagram);
            f.id(t("field.machine.block"), IdField.Kind.BLOCK, () -> e.block, v -> e.block = v, env(), t("help.machine.block"));
            f.integer(t("field.machine.interval"), 1, 12000, () -> e.interval, v -> e.interval = v, t("help.machine.interval"));
            f.id(t("field.machine.particle"), IdField.Kind.PARTICLE, () -> e.particle, v -> e.particle = v, env(), t("help.machine.particle"));

            f.section(t("section.machine.io"), t("help.machine.io"));
            List<String> resources = c.p.all(Resource.class).stream().map(r -> r.name).toList();
            if (resources.isEmpty()) f.add(Ui.help(t("help.machine.no_resources", t("types.resource"))));
            ListEditor<MachineIO> io = new ListEditor<>(e.io, () -> {
                MachineIO x = new MachineIO();
                if (!resources.isEmpty()) x.resource = resources.get(0);
                return x;
            }, (x, i) -> t("machine.io_title", x.resource.isBlank() ? "?" : x.resource, t("machine.mode." + x.mode), x.amount), (x, xf) -> {
                ref(xf, t("field.machine.resource"), resources, false, () -> x.resource, v -> x.resource = v, null);
                xf.choice(t("field.machine.mode"), MODES, m -> t("machine.mode." + m), () -> x.mode, v -> x.mode = v, t("help.machine.mode"));
                xf.integer(t("field.machine.amount"), 0, 1_000_000, () -> x.amount, v -> x.amount = v, t("help.machine.amount"));
                xf.integer(t("field.machine.capacity"), 1, 100_000_000, () -> x.capacity, v -> x.capacity = v, t("help.machine.capacity"));
                xf.showIf(() -> {
                    Resource r = resource(x.resource);
                    return r == null || !r.model.equals("stress");
                });
            }, f::changed, t("machine.add_io"));
            f.add(io);

            f.section(t("section.machine.transfer"), t("help.machine.transfer"));
            f.choice(t("field.machine.transfer"), TRANSFERS, x -> t("machine.transfer." + x), () -> e.transfer, v -> e.transfer = v);
            f.decimal(t("field.machine.range"), () -> e.range, v -> e.range = v, t("help.machine.range"));
            f.showIf(() -> e.transfer.equals("radius") || e.transfer.equals("beam"));
            f.integer(t("field.machine.rate"), 1, 1_000_000, () -> e.transferRate, v -> e.transferRate = v, t("help.machine.rate"));
            f.showIf(() -> !e.transfer.equals("none"));
            f.id(t("field.machine.beam_particle"), IdField.Kind.PARTICLE, () -> e.beamParticle, v -> e.beamParticle = v, env());
            f.showIf(() -> e.transfer.equals("beam"));

            f.section(t("section.machine.requirements"), t("help.machine.requirements"));
            List<String> multiblocks = c.p.all(Multiblock.class).stream().map(m -> m.name).toList();
            ref(f, t("field.machine.multiblock"), multiblocks, true, () -> e.multiblock, v -> e.multiblock = v, t("help.machine.multiblock"));
            f.choice(t("field.machine.celestial"), CELESTIAL, x -> t("choice." + x), () -> e.celestial, v -> e.celestial = v, t("help.machine.celestial"));
            f.integer(t("field.machine.rpm"), 0, 1024, () -> e.rpm, v -> e.rpm = v, t("help.machine.rpm"));
            f.choice(t("field.machine.overload"), OVERLOAD, x -> t("machine.overload." + x), () -> e.overload, v -> e.overload = v, t("help.machine.overload"));

            SystemEditors.logic(this, t("section.machine.on_cycle"), t("help.machine.on_cycle"), e.onCycle, () -> t("machine.start.cycle", e.interval));
            SystemEditors.logic(this, t("section.machine.on_place"), t("help.machine.on_place"), e.onPlace, () -> t("machine.start.place"));
            SystemEditors.logic(this, t("section.machine.on_break"), t("help.machine.on_break"), e.onBreak, () -> t("machine.start.break"));
            SystemEditors.logic(this, t("section.machine.on_overload"), t("help.machine.on_overload"), e.onOverload, () -> t("machine.start.overload"));
            f.section(t("section.machine.functions"));
            f.add(Ui.help(t("help.machine.functions", c.ns(), e.name)));
        }

        @Override
        protected String idText() {
            return c.ns() + ":machines/" + e.name + "/build";
        }

        @Override
        protected void refresh() {
            VBox in = new VBox(6), out = new VBox(6);
            in.setAlignment(Pos.CENTER_RIGHT);
            out.setAlignment(Pos.CENTER_LEFT);
            StringBuilder sum = new StringBuilder();
            for (MachineIO io : e.io) {
                Resource r = resource(io.resource);
                String unit = r == null ? "" : " " + r.unit;
                String hex = r == null ? "#9E9E9E" : r.color;
                boolean stress = r != null && r.model.equals("stress");
                String text = (io.resource.isBlank() ? "?" : io.resource) + "  " + io.amount + unit;
                switch (io.mode) {
                    case "consume" -> in.getChildren().add(pill(text, hex));
                    case "store" -> {
                        in.getChildren().add(pill(text, hex));
                        out.getChildren().add(pill(t("machine.mode.store") + "  " + io.capacity + unit, hex));
                    }
                    default -> out.getChildren().add(pill(text, hex));
                }
                double perSecond = io.amount * 20.0 / Math.max(1, e.interval);
                if (!sum.isEmpty()) sum.append('\n');
                sum.append(stress
                        ? t("machine.summary.stress_" + (io.mode.equals("produce") ? "produce" : "consume"), io.amount, io.resource, e.rpm)
                        : t("machine.summary." + io.mode, Form.fmt(perSecond), unit.trim(), io.resource, io.capacity));
            }
            if (in.getChildren().isEmpty()) in.getChildren().add(Ui.label(t("machine.diagram.no_input"), "text-muted"));
            if (out.getChildren().isEmpty()) out.getChildren().add(Ui.label(t("machine.diagram.no_output"), "text-muted"));
            VBox core = new VBox(4, Ui.label(e.name, "title-4"), Ui.label(e.block.isBlank() ? t("machine.diagram.no_block") : e.block, "text-muted"),
                    Ui.label(t("machine.diagram.every", e.interval), "text-muted"));
            core.setAlignment(Pos.CENTER);
            core.getStyleClass().add("machine-core");
            core.setPadding(new Insets(10, 16, 10, 16));
            var a1 = Ui.icon("mdi2a-arrow-right-thick", 22);
            var a2 = Ui.icon("mdi2a-arrow-right-thick", 22);
            a1.getStyleClass().add("machine-arrow");
            a2.getStyleClass().add("machine-arrow");
            flow.getChildren().setAll(in, a1, core, a2, out);
            flow.setAlignment(Pos.CENTER_LEFT);

            needs.getChildren().clear();
            if (!e.multiblock.isBlank()) needs.getChildren().add(Ui.label(t("machine.need.multiblock", e.multiblock), "chip"));
            if (!e.celestial.equals("none")) needs.getChildren().add(Ui.label(t("machine.need.celestial", t("choice." + e.celestial)), "chip"));
            if (!e.transfer.equals("none")) needs.getChildren().add(Ui.label(t("machine.need.transfer", t("machine.transfer." + e.transfer),
                    e.transfer.equals("adjacent") ? "1" : Form.fmt(e.range)), "chip"));
            summary.setText(sum.toString());
        }
    }

    // ------------------------------------------------------------------ multiblock

    static final class MultiblockEditor extends EditorBase<Multiblock> {
        MultiblockEditor(Ctx c, Multiblock e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.multiblock"));
            nameRow();
            f.section(t("section.multiblock.blocks"), t("help.multiblock.blocks"));
            VoxelEditor vox = new VoxelEditor(env(), e, f::changed, t("voxel.any"));
            f.add(vox);
            f.section(t("section.multiblock.core"), t("help.multiblock.core"));
            f.integer(t("field.multiblock.core_x"), 0, 63, () -> e.coreX, v -> e.coreX = v);
            f.integer(t("field.multiblock.core_y"), 0, 63, () -> e.coreY, v -> e.coreY = v);
            f.integer(t("field.multiblock.core_z"), 0, 63, () -> e.coreZ, v -> e.coreZ = v);
            f.check(t("field.multiblock.rotations"), () -> e.rotations, v -> e.rotations = v, t("help.multiblock.rotations"));
            f.section(t("section.machine.functions"));
            f.add(Ui.help(t("help.multiblock.functions", c.ns(), e.name)));
        }

        @Override
        protected String idText() {
            return c.ns() + ":multiblock/" + e.name + "/check";
        }
    }
}
