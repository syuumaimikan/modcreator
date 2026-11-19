package modstudio.ui.pages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import modstudio.core.Element;
import modstudio.i18n.I18n;
import modstudio.mcp.AiAgentClient;
import modstudio.mcp.McpServer;
import modstudio.mcp.McpToolRegistry;
import modstudio.mcp.McpToolRegistry.ToolResult;
import modstudio.mcp.McpToolRegistry.ToolSpec;
import modstudio.ui.SettingsDialog;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * In-app AI Q&A and agentic MCP creation workspace alongside external MCP Server setup & direct MCP tool runner.
 */
public final class AiAssistantPage extends BorderPane implements WorkspaceView.Page {
    private static final Gson PRETTY_JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final WorkspaceView view;
    private final McpToolRegistry registry = new McpToolRegistry();
    private final AiAgentClient client = new AiAgentClient(registry);
    private final List<AiAgentClient.ChatMessage> history = new ArrayList<>();

    private final VBox chatFeed = new VBox(10);
    private final ScrollPane chatScroll = new ScrollPane(chatFeed);
    private final TextArea inputArea = new TextArea();
    private final Button sendBtn;
    private final Label providerBadge = Ui.muted("");

    private final ComboBox<ToolSpec> toolCombo;
    private final TextArea toolArgsArea = new TextArea("{}");
    private final TextArea toolOutputArea = new TextArea();

    public AiAssistantPage(WorkspaceView view) {
        this.view = view;
        setPadding(new Insets(16));

        Label title = Ui.label(I18n.t("page.ai"), "title-2");
        Label subtitle = Ui.help(I18n.t("ai.subtitle"));
        Button settingsBtn = Ui.button(I18n.t("action.settings"), "mdi2c-cog-outline", () -> {
            if (SettingsDialog.show(view.mainWindow())) {
                view.ws.settings.save();
                refreshProviderBadge();
            }
        });
        refreshProviderBadge();
        HBox header = Ui.hbox(10, new VBox(2, title, subtitle), Ui.spacer(), providerBadge, settingsBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 12, 0));

        // Left/Main: AI Chat + Quick Prompts
        chatFeed.setPadding(new Insets(10));
        chatScroll.setFitToWidth(true);
        chatScroll.getStyleClass().add("card");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        FlowPane quickPrompts = new FlowPane(6, 6);
        quickPrompts.getChildren().addAll(
                quickChip(I18n.t("ai.quick.validate"), I18n.t("ai.prompt.validate")),
                quickChip(I18n.t("ai.quick.ore"), I18n.t("ai.prompt.ore")),
                quickChip(I18n.t("ai.quick.armor3d"), I18n.t("ai.prompt.armor3d")),
                quickChip(I18n.t("ai.quick.hud"), I18n.t("ai.prompt.hud")),
                quickChip(I18n.t("ai.quick.kotlin"), I18n.t("ai.prompt.kotlin"))
        );

        inputArea.setPromptText(I18n.t("ai.input_prompt"));
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        HBox.setHgrow(inputArea, Priority.ALWAYS);
        sendBtn = Ui.button(I18n.t("ai.send"), "mdi2s-send", this::send);
        sendBtn.getStyleClass().add("accent");
        Button clearBtn = Ui.iconButton("mdi2t-trash-can-outline", I18n.t("action.clear"), () -> {
            history.clear();
            chatFeed.getChildren().clear();
            addSystemWelcome();
        });
        inputArea.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && (e.isControlDown() || e.isShortcutDown())) {
                send();
                e.consume();
            }
        });
        HBox composer = Ui.hbox(8, inputArea, new VBox(6, sendBtn, clearBtn));
        composer.setAlignment(Pos.BOTTOM_RIGHT);

        VBox chatPane = new VBox(10, quickPrompts, chatScroll, composer);
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        // Right Tab: MCP Server & Direct Tool Console
        toolCombo = Ui.combo(registry.tools(), t -> t == null ? "" : t.name() + " — " + t.description());
        toolCombo.setMaxWidth(Double.MAX_VALUE);
        toolCombo.setPrefWidth(410);
        toolCombo.getSelectionModel().selectFirst();
        toolArgsArea.setPrefRowCount(5);
        toolArgsArea.getStyleClass().add("mono");
        toolCombo.valueProperty().addListener((o, a, t) -> {
            if (t != null) toolArgsArea.setText(sampleArgs(t));
        });
        if (toolCombo.getValue() != null) toolArgsArea.setText(sampleArgs(toolCombo.getValue()));

        Button runToolBtn = Ui.button(I18n.t("mcp.run_tool"), "mdi2p-play-outline", this::runSelectedMcpTool);
        toolOutputArea.setEditable(false);
        toolOutputArea.setPrefRowCount(10);
        toolOutputArea.getStyleClass().add("mono");
        VBox.setVgrow(toolOutputArea, Priority.ALWAYS);

        TextArea configArea = new TextArea(McpServer.clientConfigJson(view.ws.file()));
        configArea.setEditable(false);
        configArea.setPrefRowCount(8);
        configArea.getStyleClass().add("mono");
        Button copyCfgBtn = Ui.button(I18n.t("mcp.copy_config"), "mdi2c-content-copy", () -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(configArea.getText());
            Clipboard.getSystemClipboard().setContent(cc);
            providerBadge.setText(I18n.t("mcp.copied"));
        });
        Button refreshCfgBtn = Ui.iconButton("mdi2r-refresh", I18n.t("action.refresh"), () -> configArea.setText(McpServer.clientConfigJson(view.ws.file())));

        VBox mcpToolCard = new VBox(8,
                Ui.label(I18n.t("mcp.direct_tools"), "card-title"),
                toolCombo,
                toolArgsArea,
                Ui.hbox(8, runToolBtn),
                toolOutputArea);
        mcpToolCard.getStyleClass().add("card");
        mcpToolCard.setPadding(new Insets(12));
        VBox.setVgrow(mcpToolCard, Priority.ALWAYS);

        VBox mcpConfigCard = new VBox(8,
                Ui.hbox(8, Ui.label(I18n.t("mcp.server_config"), "card-title"), Ui.spacer(), refreshCfgBtn, copyCfgBtn),
                Ui.help(I18n.t("mcp.server_help")),
                configArea);
        mcpConfigCard.getStyleClass().add("card");
        mcpConfigCard.setPadding(new Insets(12));

        VBox mcpPane = new VBox(12, mcpToolCard, mcpConfigCard);
        mcpPane.setPrefWidth(460);
        mcpPane.setMinWidth(380);

        SplitPane split = new SplitPane(chatPane, mcpPane);
        split.setDividerPositions(0.60);
        setCenter(split);

        addSystemWelcome();
    }

    private void addSystemWelcome() {
        addBubble("assistant", I18n.t("ai.welcome"), List.of());
    }

    private Button quickChip(String label, String prompt) {
        Button b = Ui.button(label, "mdi2l-lightning-bolt-outline", () -> {
            inputArea.setText(prompt);
            send();
        });
        b.getStyleClass().add("chip");
        return b;
    }

    @Override
    public void onShow() {
        refreshProviderBadge();
    }

    private void refreshProviderBadge() {
        String p = view.ws.settings.aiProvider == null ? "builtin" : view.ws.settings.aiProvider;
        providerBadge.setText(I18n.t("ai.provider." + p));
    }

    private CompletableFuture<ToolResult> executeMcpOnFx(String name, JsonObject args) {
        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            ToolResult r = registry.execute(view.ws.project, name, args);
            if (r.mutated()) {
                view.ws.changed();
                view.rebuildAfterExternalMutation();
            }
            future.complete(r);
        });
        return future;
    }

    private void send() {
        String prompt = inputArea.getText() == null ? "" : inputArea.getText().trim();
        if (prompt.isEmpty() || sendBtn.isDisabled()) return;
        inputArea.clear();
        sendBtn.setDisable(true);
        addBubble("user", prompt, List.of());

        List<AiAgentClient.ChatMessage> histCopy = new ArrayList<>(history);
        history.add(new AiAgentClient.ChatMessage("user", prompt));

        CompletableFuture.supplyAsync(() -> {
            try {
                return client.ask(view.ws.settings, view.ws.project, histCopy, prompt, this::executeMcpOnFx);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).whenComplete((reply, err) -> Platform.runLater(() -> {
            sendBtn.setDisable(false);
            if (err != null) {
                String msg = err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
                addBubble("assistant", I18n.t("ai.error", msg), List.of());
                return;
            }
            history.add(new AiAgentClient.ChatMessage("assistant", reply.text()));
            addBubble("assistant", reply.text(), reply.executions());
        }));
    }

    private void addBubble(String role, String text, List<AiAgentClient.ToolExecution> executions) {
        boolean user = "user".equals(role);
        VBox bubble = new VBox(6);
        bubble.getStyleClass().add("card");
        bubble.setPadding(new Insets(10, 12, 10, 12));
        bubble.setStyle(user ? "-fx-border-color: rgba(59,130,246,0.45);" : "");

        Label who = Ui.label(user ? I18n.t("ai.you") : I18n.t("ai.assistant"), "text-bold", "text-small");
        HBox top = Ui.hbox(6, Ui.icon(user ? "mdi2a-account-outline" : "mdi2r-robot-outline", 15), who);
        top.setAlignment(Pos.CENTER_LEFT);
        bubble.getChildren().add(top);

        Label body = new Label(text);
        body.setWrapText(true);
        body.setMaxWidth(Double.MAX_VALUE);
        bubble.getChildren().add(body);

        if (!executions.isEmpty()) {
            FlowPane pills = new FlowPane(6, 6);
            for (AiAgentClient.ToolExecution ex : executions) {
                Button chip = Ui.button(ex.tool() + (ex.error() ? " (!)" : ""), ex.error() ? "mdi2a-alert-circle-outline" : "mdi2w-wrench-outline", () -> {
                    if (!ex.targetUid().isEmpty() && view.ws.project.byUid(ex.targetUid()) != null) {
                        view.openElement(ex.targetUid());
                    }
                });
                chip.getStyleClass().add("chip");
                pills.getChildren().add(chip);
            }
            bubble.getChildren().add(pills);
        }

        chatFeed.getChildren().add(bubble);
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    private void runSelectedMcpTool() {
        ToolSpec spec = toolCombo.getValue();
        if (spec == null) return;
        JsonObject args;
        try {
            String raw = toolArgsArea.getText().trim();
            args = raw.isEmpty() ? new JsonObject() : JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception ex) {
            toolOutputArea.setText("Invalid JSON arguments: " + ex.getMessage());
            return;
        }
        ToolResult res = registry.execute(view.ws.project, spec.name(), args);
        if (res.mutated()) {
            view.ws.changed();
            view.rebuildAfterExternalMutation();
        }
        toolOutputArea.setText(res.text() + "\n\n" + (res.data() == null ? "" : modstudio.core.ProjectIO.GSON.toJson(res.data())));
    }

    private static String sampleArgs(ToolSpec spec) {
        return switch (spec.name()) {
            case "create_armor_set" -> "{\n  \"name\": \"emerald_knight\",\n  \"color\": \"#10B981\",\n  \"create3dModels\": true\n}";
            case "create_element" -> "{\n  \"type\": \"block\",\n  \"name\": \"cobalt_ore\",\n  \"fields\": {\"oreGen\": true, \"orePreventSurface\": true, \"oreMaxY\": 24}\n}";
            case "create_code_file" -> "{\n  \"name\": \"hello_kotlin\",\n  \"language\": \"kotlin\",\n  \"target\": \"mods\",\n  \"hook\": \"common_init\",\n  \"content\": \"package {package_name}.custom\\nobject HelloKotlin { @JvmStatic fun init() { println(\\\"Hello from Kotlin!\\\") } }\"\n}";
            case "create_hud_or_screen" -> "{\n  \"kind\": \"hud\",\n  \"name\": \"quest_hud\",\n  \"mode\": \"sidebar\",\n  \"title\": \"&6Quest\",\n  \"lines\": [\"&ePlayer: {player}\"],\n  \"translations\": {\"ja_jp\": {\"title\": \"&6クエスト\", \"lines\": [\"&e冒険者: {player}\"]}}\n}";
            case "list_elements" -> "{\n  \"type\": \"\"\n}";
            case "search_vanilla_ids" -> "{\n  \"kind\": \"item\",\n  \"query\": \"sword\"\n}";
            default -> schemaSampleArgs(spec);
        };
    }

    private static String schemaSampleArgs(ToolSpec spec) {
        JsonObject schema = spec.inputSchema();
        if (schema == null || !schema.has("properties") || !schema.get("properties").isJsonObject()) return "{}";
        JsonObject props = schema.getAsJsonObject("properties");
        if (props.isEmpty()) return "{}";
        JsonObject template = new JsonObject();
        for (var en : props.entrySet()) {
            JsonObject p = en.getValue().isJsonObject() ? en.getValue().getAsJsonObject() : new JsonObject();
            String type = p.has("type") ? p.get("type").getAsString() : "string";
            switch (type) {
                case "boolean" -> template.addProperty(en.getKey(), false);
                case "integer" -> template.addProperty(en.getKey(), 0);
                case "number" -> template.addProperty(en.getKey(), 1.0);
                case "array" -> template.add(en.getKey(), new JsonArray());
                case "object" -> template.add(en.getKey(), new JsonObject());
                default -> template.addProperty(en.getKey(), "");
            }
        }
        return PRETTY_JSON.toJson(template);
    }
}

