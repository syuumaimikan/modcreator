package modstudio.ui.controls;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import modstudio.core.Elements.Animation;
import modstudio.core.Elements.Model;
import modstudio.core.Rig;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.Workspace;

/**
 * Interactive 3D preview box with camera presets (Iso / Front / Side), optional hitbox toggle,
 * and bone animation selector/playback for entity, mob, armor and item editors.
 */
public final class EntityPreview3D extends VBox {
    private final ModelView3D view;
    private final ComboBox<String> animBox = new ComboBox<>();
    private final Button playBtn;
    private final CheckBox hitboxCheck = new CheckBox();
    private final ToggleButton rotateBtn = new ToggleButton();
    private final HBox animRow = Ui.hbox(6);
    private Model currentModel;
    private boolean playing;
    private double animTime;
    private long lastNanos;
    private AnimationTimer timer;
    private AnimationTimer rotateTimer;
    private long lastRotateNanos;

    public EntityPreview3D(Workspace ws, double width, double height, boolean showHitboxControl) {
        super(6);
        view = new ModelView3D(ws, width, height, true);
        view.setMinSize(width, height);
        view.setMaxSize(width, height);
        view.setShowGrid(true);
        view.setShowHitbox(showHitboxControl);

        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("preview-frame");
        frame.setMinSize(width + 2, height + 2);
        frame.setMaxSize(width + 2, height + 2);

        ToggleGroup views = new ToggleGroup();
        HBox camBar = Ui.hbox(4);
        camBar.setAlignment(Pos.CENTER_LEFT);
        ToggleButton isoBtn = null;
        for (String v : java.util.List.of("iso", "front", "side")) {
            ToggleButton tb = new ToggleButton(I18n.t("view." + v));
            tb.setToggleGroup(views);
            tb.setOnAction(ev -> view.view(v));
            if (v.equals("iso")) {
                tb.setSelected(true);
                isoBtn = tb;
            }
            camBar.getChildren().add(tb);
        }
        ToggleButton resetIso = isoBtn;
        Button resetBtn = Ui.iconButton("mdi2r-restore", I18n.t("preview.reset_camera"), () -> {
            view.view("iso");
            if (resetIso != null) resetIso.setSelected(true);
        });
        rotateBtn.setGraphic(Ui.icon("mdi2r-rotate-360"));
        rotateBtn.setTooltip(new Tooltip(I18n.t("preview.auto_rotate")));
        rotateBtn.selectedProperty().addListener((o, a, b) -> setAutoRotate(b));
        camBar.getChildren().addAll(resetBtn, rotateBtn);

        hitboxCheck.setText(I18n.t("preview.hitbox"));
        hitboxCheck.setSelected(true);
        hitboxCheck.selectedProperty().addListener((o, a, b) -> view.setShowHitbox(b));
        if (showHitboxControl) camBar.getChildren().addAll(Ui.spacer(), hitboxCheck);

        sceneProperty().addListener((o, a, s) -> {
            if (s == null) {
                stop();
                setAutoRotate(false);
            }
        });

        playBtn = Ui.iconButton("mdi2p-play", I18n.t("cutscene.play"), this::togglePlay);
        animBox.setMaxWidth(150);
        animBox.valueProperty().addListener((o, a, b) -> {
            animTime = 0;
            applyPose();
        });
        animRow.setAlignment(Pos.CENTER_LEFT);
        animRow.getChildren().addAll(Ui.label(I18n.t("preview.anim"), "text-muted"), animBox, playBtn);
        animRow.setVisible(false);
        animRow.setManaged(false);

        getChildren().addAll(camBar, frame, animRow);
    }

    public void setAutoRotate(boolean on) {
        if (rotateBtn.isSelected() != on) rotateBtn.setSelected(on);
        if (!on) {
            if (rotateTimer != null) {
                rotateTimer.stop();
                rotateTimer = null;
            }
            return;
        }
        if (rotateTimer != null) return;
        lastRotateNanos = System.nanoTime();
        rotateTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!rotateBtn.isSelected() || getScene() == null) {
                    setAutoRotate(false);
                    return;
                }
                double dt = Math.min(0.1, (now - lastRotateNanos) / 1_000_000_000.0);
                lastRotateNanos = now;
                view.rotateBy(dt * 28.0);
            }
        };
        rotateTimer.start();
    }

    public ModelView3D view() {
        return view;
    }

    public void setPreview(Model model, String overrideTexture, boolean forceTexture,
                           double scale, double hitboxW, double hitboxH, String preferredAnim) {
        boolean firstFit = currentModel == null;
        currentModel = model;
        view.setOverrideTexture(overrideTexture, forceTexture);
        view.setModelScale(scale);
        view.setHitbox(hitboxW, hitboxH);
        view.setModel(model, firstFit);

        boolean hasAnims = model != null && Rig.animated(model) && !model.animations.isEmpty();
        animRow.setVisible(hasAnims);
        animRow.setManaged(hasAnims);
        if (hasAnims) {
            String prev = animBox.getValue();
            java.util.List<String> names = model.animations.stream().map(a -> a.name).toList();
            animBox.getItems().setAll(names);
            String pick = preferredAnim != null && names.contains(preferredAnim) ? preferredAnim
                    : prev != null && names.contains(prev) ? prev : names.get(0);
            animBox.setValue(pick);
            applyPose();
        } else {
            stop();
            view.setPose(null);
        }
    }

    private void applyPose() {
        if (currentModel == null || !Rig.animated(currentModel)) {
            view.setPose(null);
            return;
        }
        Animation a = currentModel.animation(animBox.getValue());
        if (a == null && !currentModel.animations.isEmpty()) a = currentModel.animations.get(0);
        view.setPose(a == null ? null : Rig.pose(currentModel, a, animTime));
    }

    private void togglePlay() {
        if (playing) stop();
        else start();
    }

    private void start() {
        if (currentModel == null || currentModel.animations.isEmpty()) return;
        playing = true;
        playBtn.setGraphic(Ui.icon("mdi2p-pause"));
        lastNanos = System.nanoTime();
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!playing || getScene() == null) {
                    stop();
                    return;
                }
                double dt = Math.min(0.1, (now - lastNanos) / 1_000_000_000.0);
                lastNanos = now;
                Animation a = currentModel == null ? null : currentModel.animation(animBox.getValue());
                if (a == null && currentModel != null && !currentModel.animations.isEmpty()) a = currentModel.animations.get(0);
                if (a == null) {
                    stop();
                    return;
                }
                double len = Math.max(0.05, a.length);
                animTime += dt;
                if (animTime > len) {
                    if ("loop".equals(a.mode)) animTime = animTime % len;
                    else {
                        animTime = "hold".equals(a.mode) ? len : 0;
                        stop();
                    }
                }
                applyPose();
            }
        };
        timer.start();
    }

    public void stop() {
        playing = false;
        playBtn.setGraphic(Ui.icon("mdi2p-play"));
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}
