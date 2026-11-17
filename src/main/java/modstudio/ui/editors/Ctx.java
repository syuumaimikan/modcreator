package modstudio.ui.editors;

import javafx.stage.Window;
import modstudio.core.Project;
import modstudio.ui.Workspace;
import modstudio.ui.WorkspaceView;

/** What an editor needs to know about its surroundings. */
public final class Ctx {
    public final WorkspaceView view;
    public final Workspace ws;
    public final Project p;
    private final Runnable listChanged;

    public Ctx(WorkspaceView view, Runnable listChanged) {
        this.view = view;
        this.ws = view.ws;
        this.p = view.ws.project;
        this.listChanged = listChanged;
    }

    public void changed() {
        ws.changed();
    }

    /** The element list needs to redraw (name, icon or subtitle changed). */
    public void listChanged() {
        if (listChanged != null) listChanged.run();
    }

    public Window window() {
        return view.window();
    }

    public String ns() {
        return p.meta.modId;
    }
}
