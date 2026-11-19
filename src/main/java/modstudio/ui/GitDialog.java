package modstudio.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import modstudio.i18n.I18n;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Simple Git status, diff and commit dialog for the open project directory. */
public final class GitDialog {
    private GitDialog() {}

    public static void show(Window owner, Path dir) {
        Dialog<Void> d = new Dialog<>();
        Ui.styleDialog(d, owner);
        d.setTitle(I18n.t("git.title"));
        d.setHeaderText(I18n.t("git.header", dir));
        d.getDialogPane().getButtonTypes().add(new ButtonType(I18n.t("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE));

        Label branch = Ui.label("", "title-5");
        TextArea output = new TextArea();
        output.setEditable(false);
        output.getStyleClass().add("mono");
        output.setPrefRowCount(16);
        output.setPrefColumnCount(68);

        TextField commitMsg = new TextField();
        commitMsg.setPromptText(I18n.t("git.commit_prompt"));
        HBox.setHgrow(commitMsg, Priority.ALWAYS);

        Button refresh = Ui.button(I18n.t("action.refresh"), "mdi2r-refresh", null);
        Button diffBtn = Ui.button(I18n.t("git.diff"), "mdi2f-file-compare", null);
        Button logBtn = Ui.button(I18n.t("git.log"), "mdi2h-history", null);
        Button branchBtn = Ui.button(I18n.t("git.switch_branch"), "mdi2s-source-branch", null);
        Button pullBtn = Ui.button(I18n.t("git.pull"), "mdi2s-source-pull", null);
        Button pushBtn = Ui.button(I18n.t("git.push"), "mdi2u-upload", null);
        Button initBtn = Ui.button(I18n.t("git.init"), "mdi2s-source-repository", null);
        Button gitignoreBtn = Ui.button(I18n.t("git.gitignore"), "mdi2f-file-document-outline", null);
        Button commitBtn = Ui.accent(I18n.t("git.commit"), "mdi2c-check", null);

        Runnable reload = () -> {
            output.setText(I18n.t("git.loading"));
            CompletableFuture.supplyAsync(() -> inspect(dir)).thenAccept(res -> Platform.runLater(() -> {
                branch.setText(res.isRepo ? I18n.t("git.branch", res.branch) : I18n.t("git.not_repo"));
                initBtn.setDisable(res.isRepo);
                commitBtn.setDisable(!res.isRepo);
                diffBtn.setDisable(!res.isRepo);
                logBtn.setDisable(!res.isRepo);
                branchBtn.setDisable(!res.isRepo);
                pullBtn.setDisable(!res.isRepo || !res.hasRemote);
                pushBtn.setDisable(!res.isRepo || !res.hasRemote);
                output.setText(res.log);
            }));
        };

        refresh.setOnAction(e -> reload.run());
        diffBtn.setOnAction(e -> showCmd(dir, List.of("git", "diff"), output));
        logBtn.setOnAction(e -> showCmd(dir, List.of("git", "log", "-n", "25", "--oneline", "--decorate"), output));
        branchBtn.setOnAction(e -> {
            TextInputDialog ask = new TextInputDialog();
            Ui.styleDialog(ask, owner);
            ask.setTitle(I18n.t("git.switch_branch"));
            ask.setHeaderText(I18n.t("git.branch_prompt"));
            ask.showAndWait().ifPresent(name -> {
                String br = name.trim();
                if (br.isEmpty()) return;
                output.setText(I18n.t("git.loading"));
                CompletableFuture.supplyAsync(() -> {
                    String exists = exec(dir, List.of("git", "rev-parse", "--verify", br));
                    List<String> cmd = exists.startsWith("fatal:")
                            ? List.of("git", "checkout", "-b", br)
                            : List.of("git", "checkout", br);
                    return exec(dir, cmd);
                }).thenAccept(txt -> Platform.runLater(() -> {
                    reload.run();
                    if (!txt.isBlank()) output.setText(txt + "\n\n" + output.getText());
                }));
            });
        });
        pullBtn.setOnAction(e -> runCmd(dir, List.of("git", "pull", "--ff-only"), reload, output));
        pushBtn.setOnAction(e -> runCmd(dir, List.of("git", "push"), reload, output));
        initBtn.setOnAction(e -> runCmd(dir, List.of("git", "init"), reload, output));
        gitignoreBtn.setOnAction(e -> {
            try {
                Path gi = dir.resolve(".gitignore");
                String rules = ".gradle/\nbuild/\nexports/\nrun/\n*.tmp\n";
                if (!Files.exists(gi)) Files.writeString(gi, rules, StandardCharsets.UTF_8);
                reload.run();
            } catch (Exception ex) {
                output.setText(ex.toString());
            }
        });
        commitBtn.setOnAction(e -> {
            String msg = commitMsg.getText().trim();
            if (msg.isEmpty()) msg = "Update project";
            String finalMsg = msg;
            output.setText(I18n.t("git.loading"));
            CompletableFuture.supplyAsync(() -> {
                String addOut = exec(dir, List.of("git", "add", "-A"));
                String ciOut = exec(dir, List.of("git", "commit", "-m", finalMsg));
                return addOut + "\n" + ciOut;
            }).thenAccept(txt -> Platform.runLater(() -> {
                commitMsg.clear();
                reload.run();
                if (!txt.isBlank()) output.setText(txt + "\n\n" + output.getText());
            }));
        });

        FlowPane actions = new FlowPane(6, 6, diffBtn, logBtn, branchBtn, pullBtn, pushBtn, gitignoreBtn, initBtn, refresh);
        actions.setPrefWrapLength(740);
        HBox commitRow = Ui.hbox(8, commitMsg, commitBtn);
        VBox box = new VBox(8, branch, actions, output, commitRow);
        box.setPadding(new Insets(6));
        box.setPrefWidth(780);
        VBox.setVgrow(output, Priority.ALWAYS);
        d.getDialogPane().setContent(box);
        Node close = d.getDialogPane().lookupButton(d.getDialogPane().getButtonTypes().get(0));
        if (close != null) close.requestFocus();
        reload.run();
        d.showAndWait();
    }

    private record Status(boolean isRepo, boolean hasRemote, String branch, String log) {}

    private static void showCmd(Path dir, List<String> cmd, TextArea out) {
        out.setText(I18n.t("git.loading"));
        CompletableFuture.supplyAsync(() -> exec(dir, cmd)).thenAccept(txt -> Platform.runLater(() ->
                out.setText(txt.isBlank() ? "(empty)" : txt)));
    }

    private static void runCmd(Path dir, List<String> cmd, Runnable after, TextArea out) {
        out.setText(I18n.t("git.loading"));
        CompletableFuture.supplyAsync(() -> exec(dir, cmd)).thenAccept(txt -> Platform.runLater(() -> {
            after.run();
            if (!txt.isBlank()) out.setText(txt);
        }));
    }

    private static Status inspect(Path dir) {
        String inside = exec(dir, List.of("git", "rev-parse", "--is-inside-work-tree")).trim();
        if (!"true".equals(inside)) {
            return new Status(false, false, "", I18n.t("git.not_repo_hint") + "\n" + inside);
        }
        String br = exec(dir, List.of("git", "branch", "--show-current")).trim();
        if (br.isEmpty()) br = "HEAD";
        boolean hasRemote = !exec(dir, List.of("git", "remote")).trim().isEmpty();
        String st = exec(dir, List.of("git", "status", "--short"));
        String diff = exec(dir, List.of("git", "diff", "--stat"));
        StringBuilder sb = new StringBuilder();
        sb.append("=== git status ===\n").append(st.isBlank() ? "(clean)\n" : st).append("\n");
        if (!diff.isBlank()) sb.append("=== git diff --stat ===\n").append(diff);
        return new Status(true, hasRemote, br, sb.toString());
    }

    private static String exec(Path dir, List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
            byte[] bytes = p.getInputStream().readAllBytes();
            p.waitFor();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
    }
}

