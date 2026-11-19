package modstudio.mcp;

import com.google.gson.*;
import modstudio.core.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Standard Model Context Protocol (MCP) server over stdio using JSON-RPC 2.0.
 * Allows external AI coding agents (Claude Desktop, Cursor, Antigravity, Gemini CLI, etc.)
 * to inspect, validate, edit, translate, and build ModStudio projects.
 *
 * Usage: {@code modstudio --mcp [project.msproj] [--readonly]}
 */
public final class McpServer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final Path projectFile;
    private final Project project;
    private final boolean readonly;
    private final McpToolRegistry registry = new McpToolRegistry();

    public McpServer(Path projectFile, Project project, boolean readonly) {
        this.projectFile = projectFile;
        this.project = project;
        this.readonly = readonly;
        if (projectFile != null && projectFile.toAbsolutePath().getParent() != null) {
            registry.setProjectDir(projectFile.toAbsolutePath().getParent());
        }
    }

    public static int run(Path projectFile, boolean readonly) {
        Project p;
        try {
            if (projectFile != null && Files.exists(projectFile)) {
                p = ProjectIO.load(projectFile);
            } else {
                p = new Project();
                p.meta.name = projectFile != null ? projectFile.getFileName().toString().replace(".msproj", "") : "MCP Mod Project";
                p.meta.modId = Ids.toId(p.meta.name);
                if (p.meta.modId.isEmpty()) p.meta.modId = "mymod";
            }
        } catch (IOException ex) {
            System.err.println("[ModStudio MCP] Failed to open project: " + ex.getMessage());
            return 1;
        }
        new McpServer(projectFile, p, readonly).serve(System.in, System.out);
        return 0;
    }

    public void serve(InputStream inStream, PrintStream out) {
        BufferedInputStream in = new BufferedInputStream(inStream);
        try {
            while (true) {
                String line = readMessage(in);
                if (line == null) break;
                if (line.isBlank()) continue;
                JsonObject resp = handleJson(line);
                if (resp != null) {
                    out.println(GSON.toJson(resp));
                    out.flush();
                }
            }
        } catch (IOException ex) {
            System.err.println("[ModStudio MCP] I/O terminated: " + ex.getMessage());
        }
    }

    /** Handles a single JSON-RPC 2.0 message string; returns null for notifications. */
    public JsonObject handleJson(String raw) {
        JsonObject req;
        try {
            req = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception ex) {
            return errorResp(JsonNull.INSTANCE, -32700, "Parse error: " + ex.getMessage());
        }
        JsonElement id = req.has("id") ? req.get("id") : null;
        String method = req.has("method") && req.get("method").isJsonPrimitive() ? req.get("method").getAsString() : "";
        JsonObject params = req.has("params") && req.get("params").isJsonObject() ? req.getAsJsonObject("params") : new JsonObject();

        // Notifications have no id or begin with notifications/
        if (id == null || method.startsWith("notifications/")) return null;

        try {
            return switch (method) {
                case "initialize" -> okResp(id, initializeResult(params));
                case "ping" -> okResp(id, new JsonObject());
                case "tools/list" -> okResp(id, listToolsResult());
                case "tools/call" -> okResp(id, callToolResult(params));
                case "resources/list" -> okResp(id, listResourcesResult());
                case "resources/templates/list" -> okResp(id, listResourceTemplatesResult());
                case "resources/read" -> readResourceResult(id, params);
                case "prompts/list" -> okResp(id, listPromptsResult());
                case "prompts/get" -> getPromptResult(id, params);
                default -> errorResp(id, -32601, "Method not found: " + method);
            };
        } catch (Exception ex) {
            return errorResp(id, -32603, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private JsonObject initializeResult(JsonObject params) {
        String clientProto = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : "2024-11-05";
        JsonObject caps = new JsonObject();
        JsonObject toolsCap = new JsonObject();
        toolsCap.addProperty("listChanged", false);
        caps.add("tools", toolsCap);
        JsonObject resCap = new JsonObject();
        resCap.addProperty("subscribe", false);
        resCap.addProperty("listChanged", false);
        caps.add("resources", resCap);
        JsonObject promptCap = new JsonObject();
        promptCap.addProperty("listChanged", false);
        caps.add("prompts", promptCap);

        JsonObject info = new JsonObject();
        info.addProperty("name", "modstudio-mcp");
        info.addProperty("version", "1.0.0");

        JsonObject res = new JsonObject();
        res.addProperty("protocolVersion", clientProto.isBlank() ? "2024-11-05" : clientProto);
        res.add("capabilities", caps);
        res.add("serverInfo", info);
        res.addProperty("instructions", "ModStudio MCP Server managing project '" + project.meta.name + "' (modId=" + project.meta.modId + ").");
        return res;
    }

    private JsonObject listToolsResult() {
        JsonArray arr = new JsonArray();
        for (McpToolRegistry.ToolSpec t : registry.tools()) {
            JsonObject to = new JsonObject();
            to.addProperty("name", t.name());
            to.addProperty("description", t.description());
            to.add("inputSchema", t.inputSchema());
            arr.add(to);
        }
        JsonObject out = new JsonObject();
        out.add("tools", arr);
        return out;
    }

    private JsonObject callToolResult(JsonObject params) {
        String name = params.has("name") ? params.get("name").getAsString() : "";
        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();
        McpToolRegistry.ToolSpec spec = registry.spec(name);
        if (spec == null) {
            return wrapToolText(true, "Unknown tool: " + name);
        }
        if (readonly && spec.mutates()) {
            return wrapToolText(true, "Server is running in --readonly mode; cannot execute mutating tool '" + name + "'.");
        }
        McpToolRegistry.ToolResult r = registry.execute(project, name, args);
        if (r.mutated() && projectFile != null) {
            try {
                ProjectIO.save(project, projectFile);
            } catch (IOException ex) {
                return wrapToolText(true, "Tool succeeded in memory but saving " + projectFile + " failed: " + ex.getMessage());
            }
        }
        String payload = r.data() != null
                ? r.text() + "\n\n```json\n" + ProjectIO.GSON.toJson(r.data()) + "\n```"
                : r.text();
        return wrapToolText(r.isError(), payload);
    }

    private static JsonObject wrapToolText(boolean isError, String text) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "text");
        item.addProperty("text", text);
        JsonArray content = new JsonArray();
        content.add(item);
        JsonObject res = new JsonObject();
        res.add("content", content);
        res.addProperty("isError", isError);
        return res;
    }

    private JsonObject listResourcesResult() {
        JsonArray arr = new JsonArray();
        arr.add(resObj("modstudio://project/summary", "Project Summary", "Metadata, elements, and textures", "application/json"));
        arr.add(resObj("modstudio://project/validation", "Validation Problems", "Current errors and warnings", "application/json"));
        arr.add(resObj("modstudio://project/compat", "Target Compatibility", "Loader and MC version compatibility matrix", "application/json"));
        JsonObject o = new JsonObject();
        o.add("resources", arr);
        return o;
    }

    private static JsonObject resObj(String uri, String name, String desc, String mime) {
        JsonObject o = new JsonObject();
        o.addProperty("uri", uri);
        o.addProperty("name", name);
        o.addProperty("description", desc);
        o.addProperty("mimeType", mime);
        return o;
    }

    private JsonObject listResourceTemplatesResult() {
        JsonArray arr = new JsonArray();
        JsonObject tpl = new JsonObject();
        tpl.addProperty("uriTemplate", "modstudio://element/{type}/{name}");
        tpl.addProperty("name", "Element Definition");
        tpl.addProperty("description", "Full JSON definition of any project element");
        tpl.addProperty("mimeType", "application/json");
        arr.add(tpl);
        JsonObject o = new JsonObject();
        o.add("resourceTemplates", arr);
        return o;
    }

    private JsonObject readResourceResult(JsonElement id, JsonObject params) {
        String uri = params.has("uri") ? params.get("uri").getAsString() : "";
        McpToolRegistry.ToolResult r;
        if ("modstudio://project/summary".equals(uri)) {
            r = registry.execute(project, "get_project_info", new JsonObject());
        } else if ("modstudio://project/validation".equals(uri) || "modstudio://problems".equals(uri)) {
            r = registry.execute(project, "validate_project", new JsonObject());
        } else if ("modstudio://project/compat".equals(uri)) {
            r = registry.execute(project, "analyze_compat", new JsonObject());
        } else if (uri.startsWith("modstudio://element/")) {
            String rest = uri.substring("modstudio://element/".length());
            int slash = rest.indexOf('/');
            if (slash <= 0 || slash == rest.length() - 1) return errorResp(id, -32602, "Invalid element URI: " + uri);
            JsonObject a = new JsonObject();
            a.addProperty("type", rest.substring(0, slash));
            a.addProperty("name", rest.substring(slash + 1));
            r = registry.execute(project, "get_element", a);
            if (r.isError()) return errorResp(id, -32602, r.text());
        } else {
            return errorResp(id, -32602, "Unknown resource URI: " + uri);
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("uri", uri);
        entry.addProperty("mimeType", "application/json");
        entry.addProperty("text", ProjectIO.GSON.toJson(r.data()));
        JsonArray contents = new JsonArray();
        contents.add(entry);
        JsonObject out = new JsonObject();
        out.add("contents", contents);
        return okResp(id, out);
    }

    private JsonObject listPromptsResult() {
        JsonArray arr = new JsonArray();
        arr.add(promptSpec("create_mod_item_pack", "Scaffold a cohesive themed item/ore/tool/armor set with textures and recipes.",
                List.of("theme", "color")));
        arr.add(promptSpec("fix_validation_errors", "Run validate_project, inspect broken references, and fix issues safely.", List.of()));
        arr.add(promptSpec("translate_project", "Audit untranslated elements/HUDs/screens and fill translations via set_translations.",
                List.of("language")));
        JsonObject o = new JsonObject();
        o.add("prompts", arr);
        return o;
    }

    private static JsonObject promptSpec(String name, String desc, List<String> argNames) {
        JsonObject p = new JsonObject();
        p.addProperty("name", name);
        p.addProperty("description", desc);
        JsonArray args = new JsonArray();
        for (String a : argNames) {
            JsonObject ao = new JsonObject();
            ao.addProperty("name", a);
            ao.addProperty("required", false);
            args.add(ao);
        }
        p.add("arguments", args);
        return p;
    }

    private JsonObject getPromptResult(JsonElement id, JsonObject params) {
        String name = params.has("name") ? params.get("name").getAsString() : "";
        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();
        String text = switch (name) {
            case "create_mod_item_pack" -> "Using project '" + project.meta.modId + "', create a cohesive mod pack themed around '"
                    + (args.has("theme") ? args.get("theme").getAsString() : "sapphire")
                    + "' using create_element, create_procedural_texture, and create_armor_set.";
            case "fix_validation_errors" -> "Run validate_project and analyze_compat on project '" + project.meta.modId
                    + "', diagnose each error/warning, and update or rename broken references via MCP tools.";
            case "translate_project" -> "Inspect get_translations and add missing translations for language '"
                    + (args.has("language") ? args.get("language").getAsString() : "ja_jp")
                    + "' across items, blocks, entities, HUDs, and screens via set_translations.";
            default -> null;
        };
        if (text == null) return errorResp(id, -32602, "Unknown prompt: " + name);
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text);
        msg.add("content", content);
        JsonArray msgs = new JsonArray();
        msgs.add(msg);
        JsonObject out = new JsonObject();
        out.addProperty("description", name);
        out.add("messages", msgs);
        return okResp(id, out);
    }

    private static JsonObject okResp(JsonElement id, JsonElement result) {
        JsonObject o = new JsonObject();
        o.addProperty("jsonrpc", "2.0");
        o.add("id", id);
        o.add("result", result);
        return o;
    }

    private static JsonObject errorResp(JsonElement id, int code, String msg) {
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", msg);
        JsonObject o = new JsonObject();
        o.addProperty("jsonrpc", "2.0");
        o.add("id", id);
        o.add("error", err);
        return o;
    }

    /** Supports both newline-delimited JSON-RPC and Content-Length framed messages. */
    private static String readMessage(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') lineBuf.write(b);
        }
        if (b == -1 && lineBuf.size() == 0) return null;
        String firstLine = lineBuf.toString(StandardCharsets.UTF_8).trim();
        if (firstLine.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
            int len = Integer.parseInt(firstLine.substring("content-length:".length()).trim());
            while (true) {
                lineBuf.reset();
                while ((b = in.read()) != -1) {
                    if (b == '\n') break;
                    if (b != '\r') lineBuf.write(b);
                }
                if (lineBuf.size() == 0) break;
            }
            byte[] payload = in.readNBytes(len);
            return new String(payload, StandardCharsets.UTF_8);
        }
        return firstLine;
    }

    /** Generates a copy-pasteable mcpServers JSON block for Claude Desktop / Cursor / Antigravity / VS Code. */
    public static String clientConfigJson(Path projectFile) {
        String javaBin = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        String cp = System.getProperty("java.class.path", "ModStudio.jar");
        JsonArray args = new JsonArray();
        args.add("-cp");
        args.add(cp);
        args.add("modstudio.Launcher");
        args.add("--mcp");
        if (projectFile != null) args.add(projectFile.toAbsolutePath().toString());
        JsonObject srv = new JsonObject();
        srv.addProperty("command", javaBin);
        srv.add("args", args);
        JsonObject servers = new JsonObject();
        servers.add("modstudio", srv);
        JsonObject root = new JsonObject();
        root.add("mcpServers", servers);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }
}

