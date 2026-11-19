package modstudio.ui;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.util.Duration;
import modstudio.core.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An open project: change tracking, undo/redo history, autosave, validation and image caching. */
public final class Workspace {
    private static final int MAX_UNDO = 40;
    public final Project project;
    public final AppSettings settings;
    private Path file;
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final IntegerProperty revision = new SimpleIntegerProperty(0);
    private final BooleanProperty canUndo = new SimpleBooleanProperty(false);
    private final BooleanProperty canRedo = new SimpleBooleanProperty(false);
    private final ObservableList<Validator.Problem> problems = FXCollections.observableArrayList();
    private final List<Runnable> structureListeners = new ArrayList<>();
    private final List<Runnable> restoreListeners = new ArrayList<>();
    private final PauseTransition validateTimer = new PauseTransition(Duration.millis(350));
    private final PauseTransition saveTimer = new PauseTransition(Duration.seconds(2));
    private final PauseTransition undoTimer = new PauseTransition(Duration.millis(450));
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private final Map<String, Image> images = new HashMap<>();
    private String currentSnapshot;
    private boolean restoring;
    private boolean undoPending;
    private String lastSaveError;

    public Workspace(Project project, Path file, AppSettings settings) {
        this.project = project;
        this.file = file;
        this.settings = settings;
        this.currentSnapshot = ProjectIO.toJson(project);
        validateTimer.setOnFinished(e -> validate());
        undoTimer.setOnFinished(e -> commitUndo());
        saveTimer.setOnFinished(e -> {
            if (settings.autosave && dirty.get() && file != null && !settings.transientMode) {
                try {
                    save();
                } catch (IOException ex) {
                    lastSaveError = ex.getMessage();
                }
            }
        });
        validate();
    }

    public Path file() {
        return file;
    }

    public Path dir() {
        return file.toAbsolutePath().getParent();
    }

    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    public IntegerProperty revisionProperty() {
        return revision;
    }

    public BooleanProperty canUndoProperty() {
        return canUndo;
    }

    public BooleanProperty canRedoProperty() {
        return canRedo;
    }

    public ObservableList<Validator.Problem> problems() {
        return problems;
    }

    public String lastSaveError() {
        return lastSaveError;
    }

    /** Call after any edit to the project. */
    public void changed() {
        dirty.set(true);
        revision.set(revision.get() + 1);
        validateTimer.playFromStart();
        if (settings.autosave) saveTimer.playFromStart();
        if (!restoring) {
            undoPending = true;
            syncUndoProps();
            undoTimer.playFromStart();
        }
    }

    /** Call after elements were added, removed or renamed. */
    public void structureChanged() {
        changed();
        if (!restoring) commitUndo();
        for (Runnable r : new ArrayList<>(structureListeners)) r.run();
    }

    public void onStructureChanged(Runnable r) {
        structureListeners.add(r);
    }

    public void onRestored(Runnable r) {
        restoreListeners.add(r);
    }

    private void syncUndoProps() {
        canUndo.set(undoPending || !undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
    }

    private void commitUndo() {
        undoPending = false;
        undoTimer.stop();
        if (restoring) return;
        String next = ProjectIO.toJson(project);
        if (next.equals(currentSnapshot)) {
            syncUndoProps();
            return;
        }
        undoStack.push(currentSnapshot);
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        redoStack.clear();
        currentSnapshot = next;
        syncUndoProps();
    }

    /** Undoes the last project edit, returning true if state changed. */
    public boolean undo() {
        if (restoring) return false;
        undoTimer.stop();
        String live = ProjectIO.toJson(project);
        String target;
        if (undoPending && !live.equals(currentSnapshot)) {
            redoStack.push(live);
            target = currentSnapshot;
        } else if (!undoStack.isEmpty()) {
            redoStack.push(live);
            target = undoStack.pop();
        } else {
            undoPending = false;
            syncUndoProps();
            return false;
        }
        applySnapshot(target);
        return true;
    }

    /** Redoes a previously undone project edit, returning true if state changed. */
    public boolean redo() {
        if (restoring || redoStack.isEmpty()) return false;
        undoTimer.stop();
        undoStack.push(ProjectIO.toJson(project));
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        applySnapshot(redoStack.pop());
        return true;
    }

    private void applySnapshot(String snap) {
        Project parsed = ProjectIO.fromJson(snap);
        if (parsed == null) return;
        restoring = true;
        undoPending = false;
        try {
            project.meta = parsed.meta;
            project.versions.clear();
            project.versions.putAll(parsed.versions);
            project.elements.clear();
            project.elements.addAll(parsed.elements);
            project.textures.clear();
            project.textures.addAll(parsed.textures);
            project.translations.clear();
            project.translations.putAll(parsed.translations);
            currentSnapshot = snap;
            images.clear();
            thumbnails = null;
            dirty.set(true);
            revision.set(revision.get() + 1);
            validate();
            if (settings.autosave) saveTimer.playFromStart();
            syncUndoProps();
            for (Runnable r : new ArrayList<>(structureListeners)) r.run();
            for (Runnable r : new ArrayList<>(restoreListeners)) r.run();
        } finally {
            restoring = false;
        }
    }

    public void validate() {
        problems.setAll(Validator.validate(project));
    }

    public void save() throws IOException {
        if (settings.transientMode) {
            dirty.set(false);
            return;
        }
        ProjectIO.save(project, file);
        dirty.set(false);
        lastSaveError = null;
    }

    public void saveAs(Path f) throws IOException {
        this.file = f;
        save();
    }

    // ------------------------------------------------------------------ images

    /** Sharp, scaled image of a project texture, or null when missing. */
    public Image texture(String textureId, int size) {
        TextureAsset t = project.texture(textureId);
        if (t == null) return null;
        String key = t.id + "|" + t.png.hashCode() + "|" + t.frameTime + "|" + size;
        Image img = images.get(key);
        if (img == null) {
            int[] px = t.firstFrame();
            img = Ui.pixelImage(px, t.width, t.frameHeight(), size);
            images.put(key, img);
            if (images.size() > 600) images.clear();
        }
        return img;
    }

    private Thumbnails thumbnails;

    /** Small pictures of elements for lists. */
    public Thumbnails thumbnails() {
        if (thumbnails == null) thumbnails = new Thumbnails(this);
        return thumbnails;
    }

    /** Image for an item or block id when it belongs to the project. */
    public Image itemImage(String id, int size) {
        Element e = project.itemLike(id);
        if (e instanceof Elements.Item it) return texture(it.texture, size);
        if (e instanceof Elements.Block b) {
            String tex = b.shape.equals("cross") || b.shape.equals("cube") ? b.texture : b.textureSide;
            return texture(tex, size);
        }
        return null;
    }

    public String ns() {
        return project.meta.modId;
    }
}
