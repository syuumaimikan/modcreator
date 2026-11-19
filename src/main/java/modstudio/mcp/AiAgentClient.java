package modstudio.mcp;

import com.google.gson.*;
import modstudio.core.*;
import modstudio.core.Elements.CodeFile;
import modstudio.gen.J;
import modstudio.mcp.McpToolRegistry.ToolResult;
import modstudio.mcp.McpToolRegistry.ToolSpec;
import modstudio.ui.AppSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-provider AI client (Built-in offline MCP assistant, OpenAI/Ollama, Gemini, Anthropic)
 * that answers ModStudio questions and creates/edits mod content via {@link McpToolRegistry}.
 */
public final class AiAgentClient {
    public record ChatMessage(String role, String text) {}

    public record ToolExecution(String tool, JsonObject args, boolean error, String summary, String targetUid) {}

    public record AgentReply(String text, List<ToolExecution> executions) {}

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final int MAX_TOOL_ROUNDS = 8;

    private final McpToolRegistry registry;

    public AiAgentClient(McpToolRegistry registry) {
        this.registry = registry;
    }

    public AgentReply ask(AppSettings settings, Project project, List<ChatMessage> history, String userPrompt,
                          BiFunction<String, JsonObject, CompletableFuture<ToolResult>> executor) throws Exception {
        String provider = settings == null || settings.aiProvider == null ? "builtin" : settings.aiProvider.trim().toLowerCase(Locale.ROOT);
        if ("builtin".equals(provider) || provider.isEmpty()) {
            return runBuiltin(project, userPrompt, executor);
        }
        String key = settings.aiApiKey == null ? "" : settings.aiApiKey.trim();
        String baseUrl = settings.aiBaseUrl == null ? "" : settings.aiBaseUrl.trim();
        if (key.isEmpty() && !("openai".equals(provider) && (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")))) {
            // Fall back cleanly if remote key is absent on cloud endpoints
            AgentReply local = runBuiltin(project, userPrompt, executor);
            return new AgentReply("[Offline MCP mode — configure an API key in Settings for cloud LLM]\n\n" + local.text(), local.executions());
        }
        return switch (provider) {
            case "gemini" -> runGemini(settings, project, history, userPrompt, executor);
            case "anthropic" -> runAnthropic(settings, project, history, userPrompt, executor);
            default -> runOpenAi(settings, project, history, userPrompt, executor);
        };
    }

    // ------------------------------------------------------------------ Built-in smart MCP assistant

    private AgentReply runBuiltin(Project p, String prompt,
                                  BiFunction<String, JsonObject, CompletableFuture<ToolResult>> executor) {
        List<ToolExecution> execs = new ArrayList<>();
        String low = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        String uiLang = modstudio.i18n.I18n.language();
        boolean jp = "ja".equals(uiLang) || containsJapanese(prompt);
        boolean zh = !jp && ("zh".equals(uiLang) || containsChineseOnly(prompt));
        boolean ko = !jp && !zh && ("ko".equals(uiLang) || containsKorean(prompt));

        boolean wantsCreate = low.contains("create") || low.contains("add") || low.contains("make") || low.contains("generate")
                || (prompt != null && (prompt.contains("作") || prompt.contains("追加") || prompt.contains("生成")
                || prompt.contains("创建") || prompt.contains("制作") || prompt.contains("생성") || prompt.contains("추가")));

        // Handle concrete creation intents directly via MCP tools
        if (wantsCreate && (low.contains("armor") || (prompt != null && (prompt.contains("防具") || prompt.contains("鎧") || prompt.contains("装備") || prompt.contains("盔甲") || prompt.contains("갑옷"))))) {
            String base = extractSlug(prompt, "ruby_dragon");
            if (base.equals("item") || base.equals("armor")) base = "mythril";
            String color = extractColor(prompt, "#8B5CF6");
            boolean is3d = low.contains("3d") || low.contains("3ｄ") || (prompt != null && (prompt.contains("立体") || prompt.contains("モデル") || prompt.contains("모델")));
            JsonObject args = new JsonObject();
            args.addProperty("name", base);
            args.addProperty("displayNamePrefix", Ids.titleCase(base));
            args.addProperty("color", color);
            args.addProperty("durability", 33);
            args.addProperty("helmetDefense", 3);
            args.addProperty("chestDefense", 8);
            args.addProperty("legsDefense", 6);
            args.addProperty("bootsDefense", 3);
            args.addProperty("toughness", 2.0);
            args.addProperty("create3dModels", is3d);
            ToolResult r = callTool("create_armor_set", args, execs, executor);
            return new AgentReply(jp
                    ? "MCPツール `create_armor_set` を実行し、" + (is3d ? "3Dモデル付きの" : "") + "防具セット `" + base + "` とテクスチャを作成しました。\n\n" + r.text()
                    : zh ? "已执行 MCP 工具 `create_armor_set` 创建护甲套装 `" + base + "`" + (is3d ? "（含 3D 模型）" : "") + "及纹理。\n\n" + r.text()
                    : ko ? "MCP 도구 `create_armor_set` 를 실행하여 방어구 세트 `" + base + "`" + (is3d ? "(3D 모델 포함)" : "") + "와 텍스처를 생성했습니다.\n\n" + r.text()
                    : "Executed MCP tool `create_armor_set` to create armor set `" + base + "` (3D=" + is3d + ") with textures.\n\n" + r.text(), execs);
        }

        if (wantsCreate && (low.contains("tool material") || low.contains("tool_material") || low.contains("tool tier")
                || (prompt != null && (prompt.contains("ツール素材") || prompt.contains("工具材质") || prompt.contains("도구 재질"))))) {
            String name = extractSlug(prompt, "mithril");
            JsonObject args = new JsonObject();
            args.addProperty("type", "tool_material");
            args.addProperty("name", name);
            JsonObject f = new JsonObject();
            f.addProperty("displayName", Ids.titleCase(name));
            f.addProperty("durability", 1250);
            f.addProperty("speed", 8.5);
            f.addProperty("attackDamageBonus", 3.5);
            f.addProperty("enchantability", 16);
            f.addProperty("miningLevel", 3);
            args.add("fields", f);
            ToolResult r = callTool("create_element", args, execs, executor);
            return new AgentReply(jp
                    ? "カスタムツール素材 `" + name + "`（耐久 1250・採掘速度 8.5・採掘レベル 3）を作成しました。\n\n" + r.text()
                    : zh ? "已创建自定义工具材质 `" + name + "`（耐久 1250、挖掘速度 8.5、挖掘等级 3）。\n\n" + r.text()
                    : ko ? "커스텀 도구 재질 `" + name + "`(내구도 1250, 채굴 속도 8.5, 채굴 레벨 3)을 생성했습니다.\n\n" + r.text()
                    : "Created custom tool material `" + name + "` (durability 1250, speed 8.5, mining level 3).\n\n" + r.text(), execs);
        }

        if (wantsCreate && (low.contains("entity") || low.contains("mob") || (prompt != null && (prompt.contains("エンティティ") || prompt.contains("モブ") || prompt.contains("实体") || prompt.contains("生物") || prompt.contains("엔티티"))))) {
            String name = extractSlug(prompt, "shadow_guardian");
            boolean canFly = low.contains("fly") || low.contains("wing") || (prompt != null && (prompt.contains("飛") || prompt.contains("翼") || prompt.contains("飞") || prompt.contains("날")));
            boolean burns = low.contains("sun") || low.contains("daylight") || (prompt != null && (prompt.contains("日光") || prompt.contains("昼")));
            boolean tame = low.contains("tame") || (prompt != null && (prompt.contains("テイム") || prompt.contains("手懐") || prompt.contains("驯服") || prompt.contains("길들")));
            JsonObject args = new JsonObject();
            args.addProperty("type", "entity");
            args.addProperty("name", name);
            JsonObject f = new JsonObject();
            f.addProperty("kind", low.contains("hostile") || low.contains("boss") ? "hostile" : "mob");
            f.addProperty("health", low.contains("boss") ? 120.0 : 30.0);
            f.addProperty("attackDamage", 5.0);
            f.addProperty("naturalSpawn", true);
            if (canFly) f.addProperty("canFly", true);
            if (burns) f.addProperty("burnsInDaylight", true);
            if (tame) {
                f.addProperty("tameable", true);
                f.addProperty("tameItem", "minecraft:bone");
            }
            args.add("fields", f);
            ToolResult r = callTool("create_element", args, execs, executor);
            JsonObject tArgs = new JsonObject();
            tArgs.addProperty("name", name);
            tArgs.addProperty("style", "orb");
            tArgs.addProperty("primaryColor", extractColor(prompt, "#8B5CF6"));
            tArgs.addProperty("assignToType", "entity");
            tArgs.addProperty("assignToName", name);
            callTool("create_procedural_texture", tArgs, execs, executor);
            return new AgentReply(jp ? "カスタムエンティティ `" + name + "` とテクスチャを作成しました。\n\n" + r.text()
                    : zh ? "已创建自定义实体 `" + name + "` 及纹理。\n\n" + r.text()
                    : ko ? "커스텀 엔티티 `" + name + "` 와 텍스처를 생성했습니다.\n\n" + r.text()
                    : "Created custom entity `" + name + "` with procedural texture.\n\n" + r.text(), execs);
        }

        if (wantsCreate && (low.contains("ore") || prompt.contains("鉱石"))) {
            String name = extractSlug(prompt, "sapphire_ore");
            if (!name.endsWith("_ore")) name = name + "_ore";
            String color = extractColor(prompt, "#2563EB");

            JsonObject bArgs = new JsonObject();
            bArgs.addProperty("type", "block");
            bArgs.addProperty("name", name);
            JsonObject bf = new JsonObject();
            bf.addProperty("displayName", Ids.titleCase(name));
            bf.addProperty("hardness", 3.5);
            bf.addProperty("resistance", 4.0);
            bf.addProperty("requiresTool", true);
            bf.addProperty("tool", "pickaxe");
            bf.addProperty("oreGen", true);
            bf.addProperty("oreMinY", -64);
            bf.addProperty("oreMaxY", 28);
            bf.addProperty("orePreventSurface", true);
            bf.addProperty("oreSurfaceDepth", 5);
            bf.addProperty("oreDiscardAir", 0.5);
            bf.addProperty("xpMin", 2);
            bf.addProperty("xpMax", 5);
            bArgs.add("fields", bf);
            ToolResult r1 = callTool("create_element", bArgs, execs, executor);

            JsonObject tArgs = new JsonObject();
            tArgs.addProperty("name", name);
            tArgs.addProperty("style", "ore");
            tArgs.addProperty("primaryColor", color);
            tArgs.addProperty("assignToType", "block");
            tArgs.addProperty("assignToName", name);
            callTool("create_procedural_texture", tArgs, execs, executor);

            return new AgentReply(jp
                    ? "地下生成専用の鉱石ブロック `" + name + "`（`orePreventSurface=true`, `maxY=28`, 経験値ドロップ付き）とテクスチャを作成しました。\n\n" + r1.text()
                    : "Created underground-only ore block `" + name + "` (`orePreventSurface=true`, `maxY=28`, XP drops) with procedural ore texture.\n\n" + r1.text(), execs);
        }

        if (wantsCreate && (low.contains("kotlin") || low.contains("python") || low.contains("scala") || low.contains("groovy")
                || low.contains("lua") || low.contains("typescript") || low.contains("script") || prompt.contains("コード") || prompt.contains("スクリプト"))) {
            String lang = low.contains("python") ? "python"
                    : low.contains("scala") ? "scala"
                    : low.contains("groovy") ? "groovy"
                    : low.contains("lua") ? "lua"
                    : low.contains("typescript") ? "typescript"
                    : "kotlin";
            String name = extractSlug(prompt, lang + "_module");
            CodeTemplates.CodePreset preset = CodeTemplates.codePreset(switch (lang) {
                case "python" -> "python_helper";
                case "scala" -> "scala_handler";
                case "groovy" -> "groovy_handler";
                case "lua" -> "lua_script";
                case "typescript" -> "typescript_bedrock";
                default -> "kotlin_common";
            });
            CodeFile cf = new CodeFile();
            cf.name = name;
            CodeTemplates.applyCodePreset(cf, preset);
            JsonObject args = new JsonObject();
            args.addProperty("name", name);
            args.addProperty("language", lang);
            args.addProperty("target", cf.target);
            args.addProperty("path", cf.path);
            args.addProperty("hook", cf.hook);
            args.addProperty("hookMethod", cf.hookMethod);
            args.addProperty("content", cf.content);
            ToolResult r = callTool("create_code_file", args, execs, executor);
            return new AgentReply(jp
                    ? "`" + lang + "` のコード要素 `" + name + "` を作成しました（ビルド時に自動でプラグインとフックが設定されます）。\n\n" + r.text()
                    : "Created `" + lang + "` code file `" + name + "` with automatic Gradle/lifecycle setup.\n\n" + r.text(), execs);
        }

        if (wantsCreate && (low.contains("hud") || low.contains("screen") || prompt.contains("画面") || prompt.contains("ボスバー"))) {
            String name = extractSlug(prompt, "status_hud");
            JsonObject args = new JsonObject();
            args.addProperty("kind", low.contains("screen") || prompt.contains("画面") ? "screen" : "hud");
            args.addProperty("name", name);
            args.addProperty("mode", low.contains("bossbar") ? "bossbar" : "sidebar");
            args.addProperty("title", "&6" + Ids.titleCase(name));
            JsonArray lines = new JsonArray();
            lines.add("&ePlayer: {player}");
            lines.add("&aTime: {min}:{sec}");
            args.add("lines", lines);
            JsonObject trs = new JsonObject();
            JsonObject ja = new JsonObject();
            ja.addProperty("title", "&6" + name + " ステータス");
            JsonArray jaLines = new JsonArray();
            jaLines.add("&eプレイヤー: {player}");
            jaLines.add("&a残り時間: {min}:{sec}");
            ja.add("lines", jaLines);
            trs.add("ja_jp", ja);
            args.add("translations", trs);
            ToolResult r = callTool("create_hud_or_screen", args, execs, executor);
            return new AgentReply(jp
                    ? "英語と日本語 (`ja_jp`) に対応した HUD `" + name + "` を作成しました。\n\n" + r.text()
                    : "Created multi-language HUD `" + name + "` with `en_us` and `ja_jp` translations.\n\n" + r.text(), execs);
        }

        if (wantsCreate && (low.contains("model") || prompt.contains("モデル"))) {
            String name = extractSlug(prompt, "custom_model");
            String tmpl = low.contains("helmet") ? "armor_helmet"
                    : low.contains("chestplate") ? "armor_chestplate"
                    : low.contains("quadruped") || prompt.contains("四足") ? "quadruped" : "pedestal";
            JsonObject args = new JsonObject();
            args.addProperty("name", name);
            args.addProperty("template", tmpl);
            ToolResult r = callTool("create_3d_model", args, execs, executor);
            return new AgentReply((jp ? "3Dモデル `" + name + "` をテンプレート `" + tmpl + "` から作成しました。\n\n" : "Created 3D model `" + name + "` from `" + tmpl + "`.\n\n") + r.text(), execs);
        }

        if (wantsCreate && (low.contains("sword") || low.contains("item") || prompt.contains("剣") || prompt.contains("アイテム"))) {
            String name = extractSlug(prompt, "starlight_sword");
            boolean isSword = low.contains("sword") || prompt.contains("剣");
            String color = extractColor(prompt, "#F59E0B");
            JsonObject args = new JsonObject();
            args.addProperty("type", "item");
            args.addProperty("name", name);
            JsonObject fields = new JsonObject();
            fields.addProperty("displayName", Ids.titleCase(name));
            fields.addProperty("kind", isSword ? "sword" : "basic");
            if (isSword) {
                fields.addProperty("attackDamage", 8.0);
                fields.addProperty("maxDamage", 1200);
                fields.addProperty("enchantability", 18);
            }
            args.add("fields", fields);
            ToolResult r = callTool("create_element", args, execs, executor);
            JsonObject tArgs = new JsonObject();
            tArgs.addProperty("name", name);
            tArgs.addProperty("style", isSword ? "sword" : "gem");
            tArgs.addProperty("primaryColor", color);
            tArgs.addProperty("assignToType", "item");
            tArgs.addProperty("assignToName", name);
            callTool("create_procedural_texture", tArgs, execs, executor);
            return new AgentReply((jp ? "アイテム `" + name + "` とテクスチャを作成しました。\n\n" : "Created item `" + name + "` with procedural texture.\n\n") + r.text(), execs);
        }

        // Q&A / Diagnostics via MCP tools
        ToolResult info = callTool("get_project_info", new JsonObject(), execs, executor);
        ToolResult val = callTool("validate_project", new JsonObject(), execs, executor);

        StringBuilder reply = new StringBuilder();
        if (jp) {
            reply.append("現在のプロジェクト解析結果です（`").append(p.meta.modId).append("`）：\n");
            reply.append("- ").append(info.text()).append("\n");
            reply.append("- ").append(val.text()).append("\n\n");
            reply.append("可能な操作（下のコマンド例やプロンプトで直接作成・質問できます。設定から OpenAI / Gemini / Anthropic / Ollama を接続するとさらに高度なAI対話も利用できます）：\n");
            reply.append("- 「サファイアの鉱石を作って」 -> 地表生成防止付きの鉱石 + テクスチャ自動生成\n");
            reply.append("- 「ドラゴン防具セットを3Dモデル付きで作って」 -> ArmorMaterial + 防具4部位 + 3Dモデル作成\n");
            reply.append("- 「Kotlinのコードファイルを追加して」 -> Kotlin Gradle対応コード作成\n");
            reply.append("- 「日本語対応のHUDを作って」 -> `ja_jp` 翻訳付きHUD生成\n");
            reply.append("- 外部AI (Claude Desktop / Cursor / Antigravity) からは `--mcp` サーバー設定をコピーして接続できます。");
        } else if (zh) {
            reply.append("当前项目分析（`").append(p.meta.modId).append("`）：\n");
            reply.append("- ").append(info.text()).append("\n");
            reply.append("- ").append(val.text()).append("\n\n");
            reply.append("可通过自然语言直接调用 MCP 工具（如创建矿石、3D护甲、实体、多语言HUD、Kotlin脚本），或在设置中配置 OpenAI / Gemini / Anthropic / Ollama。");
        } else if (ko) {
            reply.append("현재 프로젝트 분석(`").append(p.meta.modId).append("`)：\n");
            reply.append("- ").append(info.text()).append("\n");
            reply.append("- ").append(val.text()).append("\n\n");
            reply.append("자연어로 MCP 도구를 직접 실행하거나(광석, 3D 방어구, 엔티티, 다국어 HUD, Kotlin 스크립트 생성), 설정에서 OpenAI / Gemini / Anthropic / Ollama를 연결하세요.");
        } else {
            reply.append("Project summary for `").append(p.meta.modId).append("`:\n");
            reply.append("- ").append(info.text()).append("\n");
            reply.append("- ").append(val.text()).append("\n\n");
            reply.append("You can ask me to create elements via MCP tools directly (e.g. \"Create a 3D sapphire armor set\", \"Create a ruby ore block\", \"Create a custom entity\", \"Create a Kotlin script\", \"Create a multilingual HUD\"), or connect OpenAI / Ollama / Gemini / Claude in Settings for full LLM reasoning.");
        }
        return new AgentReply(reply.toString(), execs);
    }

    private ToolResult callTool(String name, JsonObject args, List<ToolExecution> execs,
                                BiFunction<String, JsonObject, CompletableFuture<ToolResult>> executor) {
        ToolResult res = executor.apply(name, args).join();
        String uid = res.data() != null && res.data().has("uid") ? res.data().get("uid").getAsString() : "";
        execs.add(new ToolExecution(name, args, res.isError(), res.text(), uid));
        return res;
    }

    // ------------------------------------------------------------------ OpenAI / Ollama

    private AgentReply runOpenAi(AppSettings s, Project p, List<ChatMessage> history, String userPrompt,
                                 BiFunction<String, JsonObject, CompletableFuture<ToolResult>> executor) throws Exception {
        String base = (s.aiBaseUrl == null || s.aiBaseUrl.isBlank() ? "https://api.openai.com/v1" : s.aiBaseUrl.trim()).replaceAll("/+$", "");
        String url = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
        String model = s.aiModel == null || s.aiModel.isBlank() ? "gpt-4o-mini" : s.aiModel.trim();

        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt(p));
        messages.add(sys);
        for (ChatMessage m : history) {
            JsonObject mo = new JsonObject();
            mo.addProperty("role", m.role());
            mo.addProperty("content", m.text());
            messages.add(mo);
        }
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);

        JsonArray toolsArr = new JsonArray();
        for (ToolSpec t : registry.tools()) {
            JsonObject fn = new JsonObject();
            fn.addProperty("name", t.name());
            fn.addProperty("description", t.description());
            fn.add("parameters", t.inputSchema());
            JsonObject wrap = new JsonObject();
            wrap.addProperty("type", "function");
            wrap.add("function", fn);
            toolsArr.add(wrap);
        }

        List<ToolExecution> execs = new ArrayList<>();
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.add("messages", messages);
            body.add("tools", toolsArr);

            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
            if (s.aiApiKey != null && !s.aiApiKey.isBlank()) req.header("Authorization", "Bearer " + s.aiApiKey.trim());

            HttpResponse<String> resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 400) throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + resp.body());

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonObject msg = root.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            messages.add(msg);

            if (!msg.has("tool_calls") || !msg.get("tool_calls").isJsonArray() || msg.getAsJsonArray("tool_calls").isEmpty()) {
                String content = msg.has("content") && !msg.get("content").isJsonNull() ? msg.get("content").getAsString() : "";
                return new AgentReply(content, execs);
            }
            for (JsonElement tcEl : msg.getAsJsonArray("tool_calls")) {
                JsonObject tc = tcEl.getAsJsonObject();
                String tcId = tc.get("id").getAsString();
                JsonObject fn = tc.getAsJsonObject("function");
                String tName = fn.get("name").getAsString();
                String rawArgs = fn.has("arguments") ? fn.get("arguments").getAsString() : "{}";
                JsonObject tArgs = rawArgs.isBlank() ? new JsonObject() : JsonParser.parseString(rawArgs).getAsJsonObject();
                ToolResult tr = callTool(tName, tArgs, execs, executor);
                JsonObject toolMsg = new JsonObject();
                toolMsg.addProperty("role", "tool");
                toolMsg.addProperty("tool_call_id", tcId);
                toolMsg.addProperty("content", tr.text() + "\n" + GSON.toJson(tr.data()));
                messages.add(toolMsg);
            }
        }
        return new AgentReply("Reached maximum tool call iterations.", execs);
    }

    // ------------------------------------------------------------------ Google Gemini

    private AgentReply runGemini(AppSettings s, Project p, List<ChatMessage> history, String userPrompt,
                                 BiFunction<String, JsonObject, CompletableFuture<ToolResult>> executor) throws Exception {
        String model = s.aiModel == null || s.aiModel.isBlank() ? "gemini-2.5-flash" : s.aiModel.trim();
        String base = s.aiBaseUrl == null || s.aiBaseUrl.isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta"
                : s.aiBaseUrl.trim().replaceAll("/+$", "");
        String url = base + "/models/" + model + ":generateContent";

        JsonArray contents = new JsonArray();
        for (ChatMessage m : history) {
            JsonObject part = new JsonObject();
            part.addProperty("text", m.text());
            JsonObject c = new JsonObject();
            c.addProperty("role", "assistant".equals(m.role()) ? "model" : "user");
            c.add("parts", J.arr(part));
            contents.add(c);
        }
        JsonObject up = new JsonObject();
        up.addProperty("text", userPrompt);
        contents.add(J.obj("role", "user", "parts", J.arr(up)));

        JsonArray decls = new JsonArray();
        for (ToolSpec t : registry.tools()) {
            JsonObject d = new JsonObject();
            d.addProperty("name", t.name());
            d.addProperty("description", t.description());
            d.add("parameters", sanitizeGeminiSchema(t.inputSchema()));
            decls.add(d);
        }

        List<ToolExecution> execs = new ArrayList<>();
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonObject body = new JsonObject();
            body.add("systemInstruction", J.obj("parts", J.arr(J.obj("text", systemPrompt(p)))));
            body.add("contents", contents);
            body.add("tools", J.arr(J.obj("functionDeclarations", decls)));

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", s.aiApiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 400) throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + resp.body());

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray candidates = root.has("candidates") && root.get("candidates").isJsonArray() ? root.getAsJsonArray("candidates") : null;
            if (candidates == null || candidates.isEmpty()) {
                return new AgentReply("No response candidate returned by Gemini (possibly filtered by safety settings).", execs);
            }
            JsonObject cand = candidates.get(0).getAsJsonObject();
            JsonObject contentObj = cand.has("content") && cand.get("content").isJsonObject() ? cand.getAsJsonObject("content") : null;
            if (contentObj == null || !contentObj.has("parts")) {
                String finish = cand.has("finishReason") ? cand.get("finishReason").getAsString() : "UNKNOWN";
                return new AgentReply("Gemini finished without content (" + finish + ").", execs);
            }
            contents.add(contentObj);

            JsonArray funcRespParts = new JsonArray();
            StringBuilder textOut = new StringBuilder();
            for (JsonElement partEl : contentObj.getAsJsonArray("parts")) {
                JsonObject po = partEl.getAsJsonObject();
                if (po.has("text")) textOut.append(po.get("text").getAsString());
                if (po.has("functionCall")) {
                    JsonObject fc = po.getAsJsonObject("functionCall");
                    String tName = fc.get("name").getAsString();
                    JsonObject tArgs = fc.has("args") && fc.get("args").isJsonObject() ? fc.getAsJsonObject("args") : new JsonObject();
                    ToolResult tr = callTool(tName, tArgs, execs, executor);
                    funcRespParts.add(J.obj("functionResponse", J.obj("name", tName,
                            "response", J.obj("result", tr.text(), "data", tr.data()))));
                }
            }
            if (funcRespParts.isEmpty()) return new AgentReply(textOut.toString(), execs);
            contents.add(J.obj("role", "user", "parts", funcRespParts));
        }
        return new AgentReply("Reached maximum tool call iterations.", execs);
    }

    // ------------------------------------------------------------------ Anthropic Claude

    private AgentReply runAnthropic(AppSettings s, Project p, List<ChatMessage> history, String userPrompt,
                                    BiFunction<String, JsonObject, CompletableFuture<ToolResult>> executor) throws Exception {
        String model = s.aiModel == null || s.aiModel.isBlank() ? "claude-sonnet-4-5" : s.aiModel.trim();
        String base = s.aiBaseUrl == null || s.aiBaseUrl.isBlank()
                ? "https://api.anthropic.com/v1"
                : s.aiBaseUrl.trim().replaceAll("/+$", "");
        String url = base.endsWith("/messages") ? base : base + "/messages";

        JsonArray messages = new JsonArray();
        for (ChatMessage m : history) {
            messages.add(J.obj("role", m.role(), "content", m.text()));
        }
        messages.add(J.obj("role", "user", "content", userPrompt));

        JsonArray toolsArr = new JsonArray();
        for (ToolSpec t : registry.tools()) {
            JsonObject to = new JsonObject();
            to.addProperty("name", t.name());
            to.addProperty("description", t.description());
            to.add("input_schema", t.inputSchema());
            toolsArr.add(to);
        }

        List<ToolExecution> execs = new ArrayList<>();
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 4096);
            body.addProperty("system", systemPrompt(p));
            body.add("messages", messages);
            body.add("tools", toolsArr);

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", s.aiApiKey.trim())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 400) throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + resp.body());

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray blocks = root.getAsJsonArray("content");
            messages.add(J.obj("role", "assistant", "content", blocks));

            JsonArray toolResults = new JsonArray();
            StringBuilder textOut = new StringBuilder();
            for (JsonElement bel : blocks) {
                JsonObject bo = bel.getAsJsonObject();
                String btype = bo.get("type").getAsString();
                if ("text".equals(btype)) textOut.append(bo.get("text").getAsString());
                else if ("tool_use".equals(btype)) {
                    String id = bo.get("id").getAsString();
                    String tName = bo.get("name").getAsString();
                    JsonObject tArgs = bo.has("input") && bo.get("input").isJsonObject() ? bo.getAsJsonObject("input") : new JsonObject();
                    ToolResult tr = callTool(tName, tArgs, execs, executor);
                    toolResults.add(J.obj("type", "tool_result", "tool_use_id", id,
                            "content", tr.text() + "\n" + GSON.toJson(tr.data()), "is_error", tr.isError()));
                }
            }
            if (toolResults.isEmpty()) return new AgentReply(textOut.toString(), execs);
            messages.add(J.obj("role", "user", "content", toolResults));
        }
        return new AgentReply("Reached maximum tool call iterations.", execs);
    }

    // ------------------------------------------------------------------ utilities

    private static String systemPrompt(Project p) {
        return "You are the built-in AI & MCP assistant for ModStudio, an application that builds Minecraft Java/Bedrock mods, plugins, datapacks, and resource packs. "
                + "Current project: name='" + p.meta.name + "', modId='" + p.meta.modId + "', targets=" + p.meta.targets + ", mcVersions=" + p.meta.mcVersions + ". "
                + "Use the provided MCP tools whenever the user asks to inspect, validate, translate, build, or create/modify elements, 3D armor/entity models, underground ores, HUDs, or code files. "
                + "Reply in the user's language (e.g., Japanese or English), cite exact element names/paths returned by tools, and keep explanations concise and actionable.";
    }

    private static boolean containsJapanese(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '\u3040' && c <= '\u30FF') || (c >= '\u4E00' && c <= '\u9FFF' && !s.contains("创建") && !s.contains("制作") && !s.contains("的"))) return true;
        }
        return false;
    }

    private static boolean containsChineseOnly(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4E00' && c <= '\u9FFF') return true;
        }
        return false;
    }

    private static boolean containsKorean(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\uAC00' && c <= '\uD7AF') return true;
        }
        return false;
    }

    private static JsonObject sanitizeGeminiSchema(JsonObject in) {
        JsonObject out = in.deepCopy();
        out.remove("$schema");
        out.remove("additionalProperties");
        if (out.has("properties") && out.get("properties").isJsonObject() && out.getAsJsonObject("properties").isEmpty()) {
            out.remove("properties");
        }
        return out;
    }

    private static String extractSlug(String prompt, String fallback) {
        Matcher m = Pattern.compile("[a-z][a-z0-9_]{2,}").matcher(prompt.toLowerCase(Locale.ROOT));
        Set<String> stop = Set.of("create", "make", "add", "with", "that", "this", "from", "into", "item", "block", "armor", "model",
                "kotlin", "scala", "groovy", "python", "lua", "typescript", "javascript", "screen", "mod", "minecraft", "3d");
        while (m.find()) {
            String tok = m.group();
            if (!stop.contains(tok)) return tok;
        }
        return fallback;
    }

    private static String extractColor(String prompt, String def) {
        Matcher m = Pattern.compile("#[0-9a-fA-F]{6}").matcher(prompt);
        if (m.find()) return m.group();
        String low = prompt.toLowerCase(Locale.ROOT);
        if (low.contains("red") || prompt.contains("赤") || low.contains("ruby") || prompt.contains("ルビー")) return "#E11D48";
        if (low.contains("blue") || prompt.contains("青") || low.contains("sapphire") || prompt.contains("サファイア")) return "#2563EB";
        if (low.contains("green") || prompt.contains("緑") || low.contains("emerald") || prompt.contains("エメラルド")) return "#10B981";
        if (low.contains("gold") || prompt.contains("金") || low.contains("yellow") || prompt.contains("黄")) return "#F59E0B";
        if (low.contains("purple") || prompt.contains("紫") || low.contains("dragon")) return "#8B5CF6";
        return def;
    }
}
