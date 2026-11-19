package modstudio.mcp;

import com.google.gson.*;
import modstudio.build.BuildService;
import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.gen.Exporter;
import modstudio.gen.GenContext;
import modstudio.gen.Generators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Shared registry of ModStudio MCP tools callable both by external MCP clients (stdio JSON-RPC 2.0)
 * and by the built-in AI assistant / in-app tool runner.
 */
public final class McpToolRegistry {

    public record ToolSpec(String name, String description, JsonObject inputSchema, boolean mutates) {}

    public record ToolResult(boolean isError, String text, JsonObject data, boolean mutated) {
        public static ToolResult ok(String text, JsonObject data, boolean mutated) {
            return new ToolResult(false, text, data, mutated);
        }
        public static ToolResult err(String message) {
            JsonObject o = new JsonObject();
            o.addProperty("error", message);
            return new ToolResult(true, message, o, false);
        }
    }

    @FunctionalInterface
    public interface McpToolHandler {
        ToolResult execute(Project p, JsonObject args);
    }

    public record McpTool(ToolSpec spec, McpToolHandler handler) {}

    private static final Map<String, McpTool> GLOBAL_TOOLS = new LinkedHashMap<>();

    private final List<ToolSpec> tools = new ArrayList<>();
    private final Map<String, ToolSpec> byName = new LinkedHashMap<>();
    private final Map<String, McpToolHandler> customHandlers = new LinkedHashMap<>();
    private Path projectDir;

    public McpToolRegistry() {
        registerAll();
        synchronized (GLOBAL_TOOLS) {
            for (McpTool t : GLOBAL_TOOLS.values()) registerTool(t);
        }
    }

    public void setProjectDir(Path dir) {
        this.projectDir = dir;
    }

    /** Registers a global custom MCP tool made available to every registry instance (used by ModStudioPlugin SPI). */
    public static synchronized void registerGlobalTool(McpTool tool) {
        if (tool != null && tool.spec() != null && tool.spec().name() != null && !tool.spec().name().isBlank()) {
            GLOBAL_TOOLS.put(tool.spec().name(), tool);
        }
    }

    /** Registers or overrides a custom MCP tool on this registry instance. */
    public void registerTool(McpTool tool) {
        if (tool == null || tool.spec() == null || tool.spec().name() == null || tool.handler() == null) return;
        ToolSpec t = tool.spec();
        if (!byName.containsKey(t.name())) tools.add(t);
        else {
            for (int i = 0; i < tools.size(); i++) {
                if (tools.get(i).name().equals(t.name())) { tools.set(i, t); break; }
            }
        }
        byName.put(t.name(), t);
        customHandlers.put(t.name(), tool.handler());
    }

    public List<ToolSpec> tools() {
        return Collections.unmodifiableList(tools);
    }

    public ToolSpec spec(String name) {
        return byName.get(name);
    }

    private void add(String name, String desc, boolean mutates, JsonObject schema) {
        ToolSpec t = new ToolSpec(name, desc, schema, mutates);
        tools.add(t);
        byName.put(name, t);
    }

    public static JsonObject schema(Map<String, JsonObject> props, String... required) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        JsonObject p = new JsonObject();
        props.forEach(p::add);
        root.add("properties", p);
        if (required.length > 0) {
            JsonArray req = new JsonArray();
            for (String r : required) req.add(r);
            root.add("required", req);
        }
        return root;
    }

    public static JsonObject prop(String type, String desc) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("description", desc);
        return o;
    }

    public static JsonObject propObj(String desc) {
        return prop("object", desc);
    }

    public static JsonObject propArr(String itemType, String desc) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "array");
        o.addProperty("description", desc);
        JsonObject items = new JsonObject();
        items.addProperty("type", itemType);
        o.add("items", items);
        return o;
    }

    private void registerAll() {
        add("get_project_info", "Returns ModStudio project metadata (modId, targets, mcVersions, languages), element counts by type, and texture counts.", false,
                schema(Map.of()));
        add("list_element_types", "Lists all available ModStudio element types, categories, and current element counts.", false,
                schema(Map.of()));
        add("list_elements", "Lists elements in the project, optionally filtered by element type id or text query.", false,
                schema(Map.of(
                        "type", prop("string", "Optional element type id (e.g. item, block, tool_material, armor_material, entity, model, hud, screen, code_file)."),
                        "query", prop("string", "Optional case-insensitive substring filter matching name or subtitle."))));
        add("get_element", "Gets the full JSON definition of a project element by uid or by (type + name).", false,
                schema(Map.of(
                        "uid", prop("string", "Element UID."),
                        "type", prop("string", "Element type id (if uid omitted)."),
                        "name", prop("string", "Element name (if uid omitted)."))));
        add("list_textures", "Lists all textures in the project with their id, name, width, height, and animation frame count.", false,
                schema(Map.of()));
        add("validate_project", "Runs the ModStudio project validator and returns all errors, warnings, and info problems.", false,
                schema(Map.of()));
        add("analyze_compat", "Analyzes cross-target compatibility (FULL / FALLBACK / NONE) across all configured targets and Minecraft versions.", false,
                schema(Map.of()));
        add("get_translations", "Returns localization entries and exported language maps (en_us, ja_jp, etc.) including HUD and Screen translations.", false,
                schema(Map.of("lang", prop("string", "Optional language code (e.g. ja_jp) to filter output."))));
        add("search_vanilla_ids", "Searches vanilla Minecraft identifier catalogs (item, block, entity, effect, enchantment, sound, particle, damage_type, dimension).", false,
                schema(Map.of(
                        "kind", prop("string", "Catalog kind: item, block, entity, effect, enchantment, sound, particle, damage_type, dimension."),
                        "query", prop("string", "Search query substring.")), "query"));
        add("preview_generated_file", "Generates files in memory for a target and MC version. Returns file list, or file contents if path is given.", false,
                schema(Map.of(
                        "target", prop("string", "Target id (fabric, neoforge, forge, quilt, paper, folia, datapack, resourcepack, bedrock). Defaults to first project target."),
                        "mcVersion", prop("string", "Minecraft version id (e.g. 1.21.1, 1.21.8, 26.1). Defaults to latest configured version."),
                        "path", prop("string", "Optional generated file path to read contents."))));

        add("update_project_meta", "Updates project metadata such as name, modId, version, author, description, targets, mcVersions, or languages.", true,
                schema(Map.ofEntries(
                        Map.entry("name", prop("string", "Display name.")),
                        Map.entry("modId", prop("string", "Mod ID namespace (lowercase [a-z0-9_]).")),
                        Map.entry("version", prop("string", "Version string.")),
                        Map.entry("author", prop("string", "Author string.")),
                        Map.entry("description", prop("string", "Description.")),
                        Map.entry("targets", propArr("string", "Export target ids.")),
                        Map.entry("mcVersions", propArr("string", "Target Minecraft versions (e.g. [\"1.21.1\", \"1.21.4\"]).")),
                        Map.entry("languages", propArr("string", "Language codes (e.g. [\"en_us\", \"ja_jp\"]).")),
                        Map.entry("packageName", prop("string", "Base Java package name (e.g. com.example.mymod).")),
                        Map.entry("license", prop("string", "License identifier (e.g. MIT, Apache-2.0).")),
                        Map.entry("website", prop("string", "Project homepage URL.")),
                        Map.entry("customGradle", prop("string", "Raw Gradle snippet appended to build.gradle.")),
                        Map.entry("jvmArgs", prop("string", "JVM arguments for run tasks.")))));
        add("create_element", "Creates any ModStudio element (item, block, tool_material, armor_material, entity, world_feature, recipe, procedure, code_file, hud, screen, model, etc.) and patches fields onto it.", true,
                schema(Map.of(
                        "type", prop("string", "ElementType id (e.g. item, block, tool_material, armor_material, entity, world_feature, code_file, recipe, hud, screen, model, status_effect)."),
                        "name", prop("string", "Element id/name (snake_case, e.g. sapphire_ore)."),
                        "fields", propObj("JSON object containing element properties matching ModStudio fields (e.g. oreGen, oreMinY, oreMaxY, orePreventSurface, durability, speed, miningLevel, canFly, burnsInDaylight, tameable, helmetModel).")), "type", "name"));
        add("update_element", "Updates an existing element by uid or (type + name) with a partial fields JSON object (handles rename refactor automatically if name changes).", true,
                schema(Map.of(
                        "uid", prop("string", "Element UID."),
                        "type", prop("string", "Element type id (if uid omitted)."),
                        "name", prop("string", "Element name (if uid omitted)."),
                        "fields", propObj("Fields to update on the element.")), "fields"));
        add("delete_element", "Removes an element from the project by uid or (type + name).", true,
                schema(Map.of(
                        "uid", prop("string", "Element UID."),
                        "type", prop("string", "Element type id."),
                        "name", prop("string", "Element name."))));
        add("create_procedural_texture", "Generates a crisp procedural pixel-art PNG texture (sword, pickaxe, axe, gem, ingot, ore, block, brick, potion, armor, orb, solid) and optionally links it to an element.", true,
                schema(Map.of(
                        "name", prop("string", "Texture name."),
                        "style", prop("string", "Art style: ore, block, brick, gem, ingot, sword, pickaxe, axe, potion, armor, orb, solid."),
                        "primaryColor", prop("string", "Hex primary color (e.g. #2E86DE)."),
                        "secondaryColor", prop("string", "Hex secondary/stone/handle color (e.g. #7F8C8D)."),
                        "size", prop("integer", "Pixel size (16, 32, or 64). Defaults to 16."),
                        "assignToType", prop("string", "Optional element type to attach texture id (e.g. item, block, entity)."),
                        "assignToName", prop("string", "Optional element name to attach texture id.")), "name", "style"));
        add("create_3d_model", "Creates or updates a 3D model element from a template (armor_helmet, armor_chestplate, armor_leggings, armor_boots, quadruped, biped, pedestal, etc.) or custom bones and cubes.", true,
                schema(Map.of(
                        "name", prop("string", "Model name."),
                        "template", prop("string", "Optional template name from Models.TEMPLATES."),
                        "kind", prop("string", "Model kind: block, item, or entity."),
                        "textureWidth", prop("integer", "UV texture width (e.g. 16 or 64)."),
                        "textureHeight", prop("integer", "UV texture height (e.g. 16 or 64)."),
                        "bones", propArr("object", "Optional array of {name, parent, pivot:[x,y,z], rotation:[x,y,z]}."),
                        "cubes", propArr("object", "Optional array of {name, bone, from:[x,y,z], to:[x,y,z], rotation, axis, origin:[x,y,z], inflate}.")), "name"));
        add("create_armor_set", "Creates a complete armor set (ArmorMaterial + helmet/chestplate/leggings/boots Items + textures + optional 3D models & set bonus effect) in one call.", true,
                schema(Map.ofEntries(
                        Map.entry("name", prop("string", "Base armor set name (e.g. dragon_scale).")),
                        Map.entry("displayNamePrefix", prop("string", "Display name prefix (e.g. Dragon Scale).")),
                        Map.entry("color", prop("string", "Primary hex color for generated textures (e.g. #9B59B6).")),
                        Map.entry("durability", prop("integer", "Durability multiplier (e.g. 35).")),
                        Map.entry("helmetDefense", prop("integer", "Helmet defense points.")),
                        Map.entry("chestDefense", prop("integer", "Chestplate defense points.")),
                        Map.entry("legsDefense", prop("integer", "Leggings defense points.")),
                        Map.entry("bootsDefense", prop("integer", "Boots defense points.")),
                        Map.entry("toughness", prop("number", "Armor toughness.")),
                        Map.entry("create3dModels", prop("boolean", "If true, creates 3D armor models (helmet/chestplate/leggings/boots) and links them to the ArmorMaterial.")),
                        Map.entry("setBonusEffect", prop("string", "Optional status effect ID granted when full set is worn."))), "name"));
        add("create_hud_or_screen", "Creates/updates a multi-language HUD (sidebar/bossbar/actionbar) or CustomScreen (screen/overlay) along with optional translations per language.", true,
                schema(Map.of(
                        "kind", prop("string", "hud or screen."),
                        "name", prop("string", "Element name."),
                        "mode", prop("string", "HUD kind (sidebar, bossbar, actionbar) or Screen mode (screen, overlay)."),
                        "title", prop("string", "Default English title."),
                        "lines", propArr("string", "HUD lines (up to 15) if kind=hud."),
                        "widgets", propArr("object", "ScreenWidget JSON objects if kind=screen."),
                        "translations", propObj("Map lang -> {title, lines:[...]} or {title, widgets:[{text, tooltip, placeholder, options}]}.")), "kind", "name"));
        add("create_code_file", "Creates or updates a multi-language source file (Java, Kotlin, Scala, Groovy, TypeScript, JavaScript, Python, Lua, GLSL, JSON, TOML, YAML, mcfunction) with optional lifecycle hook.", true,
                schema(Map.of(
                        "name", prop("string", "Element name."),
                        "language", prop("string", "Language: auto, java, kotlin, scala, groovy, typescript, javascript, python, lua, glsl, json, toml, yaml, mcfunction."),
                        "target", prop("string", "Target: all, mods, fabric, neoforge, forge, quilt, paper, folia, bedrock, datapack."),
                        "path", prop("string", "Output relative path (supports {package}, {modid}). Inferred if omitted."),
                        "hook", prop("string", "Lifecycle hook: none, common_init, client_init, event_bus, register_commands, server_tick."),
                        "hookMethod", prop("string", "Static/object method name for the hook (default init)."),
                        "content", prop("string", "Source code content.")), "name", "content"));
        add("set_translations", "Adds project languages and sets translations for element fields (items, blocks, HUDs, screens, etc.) or custom lang keys.", true,
                schema(Map.of(
                        "languages", propArr("string", "Optional language codes to add to project (e.g. [\"en_us\", \"ja_jp\"])."),
                        "entries", propArr("object", "Array of {lang, type, name, field, value} for element localization (field e.g. name, title, line.0, w.0.text, tooltip) OR {lang, key, value} for standalone keys.")), "entries"));
        add("build_or_export", "Generates all files for configured targets into outputDir (for quick testing/CLI export).", false,
                schema(Map.of(
                        "outputDir", prop("string", "Directory path to write generated targets into."),
                        "target", prop("string", "Optional single target id to export (exports all configured targets if omitted)."),
                        "mcVersion", prop("string", "Optional MC version id (defaults to latest configured version).")), "outputDir"));
        add("get_texture", "Reads a texture asset by id or name, returning dimensions, animation info, and Base64 PNG bytes.", false,
                schema(Map.of(
                        "id", prop("string", "Texture id."),
                        "name", prop("string", "Texture name if id omitted."))));
        add("import_texture", "Imports a PNG image from a local file path or Base64 string into the project and optionally links it to an element.", true,
                schema(Map.of(
                        "name", prop("string", "Texture asset name."),
                        "path", prop("string", "Local PNG file path."),
                        "base64", prop("string", "Base64-encoded PNG data (used if path omitted)."),
                        "assignToType", prop("string", "Optional element type id."),
                        "assignToName", prop("string", "Optional element name.")), "name"));
        add("delete_texture", "Deletes a texture from the project by id or name and reports any referencing elements.", true,
                schema(Map.of(
                        "id", prop("string", "Texture id."),
                        "name", prop("string", "Texture name if id omitted."))));
        add("rename_element", "Safely renames an element and refactors all cross-references (recipes, loot tables, steps, models, menus, translations).", true,
                schema(Map.of(
                        "uid", prop("string", "Element UID."),
                        "type", prop("string", "Element type id (if uid omitted)."),
                        "name", prop("string", "Current element name (if uid omitted)."),
                        "newName", prop("string", "New element name.")), "newName"));
        add("find_usages", "Finds all project elements referencing a given element name or full resource ID.", false,
                schema(Map.of("target", prop("string", "Element name or resource id (e.g. sapphire or mymod:sapphire).")), "target"));
        add("import_artifact", "Imports elements and textures from an external .jar, .zip, or .msproj file into the current project.", true,
                schema(Map.of(
                        "path", prop("string", "File path to .jar, .zip, or .msproj."),
                        "overwrite", prop("boolean", "If true, replaces existing elements with the same type+name.")), "path"));
        add("upgrade_project", "Upgrades the project target Minecraft version, updating legacy IDs, folders, pack formats, and toolchain settings.", true,
                schema(Map.of("mcVersion", prop("string", "Target Minecraft version id (e.g. 1.21.8, 26.1).")), "mcVersion"));
        add("manage_secrets", "Lists referenced/stored API secret names (values masked) or sets/removes a secret in local secrets storage.", true,
                schema(Map.of(
                        "action", prop("string", "list, set, or remove."),
                        "name", prop("string", "Secret key name (for set/remove)."),
                        "value", prop("string", "Secret value (for set)."),
                        "projectDir", prop("string", "Optional project directory if server has no project file path."))));
        add("build_jar", "Exports and compiles a Gradle target into a JAR file via BuildService.", false,
                schema(Map.of(
                        "outputDir", prop("string", "Working build directory (defaults to a temporary directory if omitted)."),
                        "target", prop("string", "Gradle target id (defaults to first Gradle target)."),
                        "mcVersion", prop("string", "Minecraft version id (defaults to latest configured version)."),
                        "javaHome", prop("string", "Optional JDK path override."))));
    }

    // ------------------------------------------------------------------ execution

    public ToolResult execute(Project p, String toolName, JsonObject args) {
        if (p == null) return ToolResult.err("No project loaded.");
        if (args == null) args = new JsonObject();
        McpToolHandler custom = customHandlers.get(toolName);
        if (custom != null) {
            try {
                return custom.execute(p, args);
            } catch (Exception ex) {
                return ToolResult.err("Tool '" + toolName + "' failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        try {
            return switch (toolName) {
                case "get_project_info" -> getProjectInfo(p);
                case "list_element_types" -> listElementTypes(p);
                case "list_elements" -> listElements(p, args);
                case "get_element" -> getElement(p, args);
                case "list_textures" -> listTextures(p);
                case "validate_project" -> validateProject(p);
                case "analyze_compat" -> analyzeCompat(p);
                case "get_translations" -> getTranslations(p, args);
                case "search_vanilla_ids" -> searchVanilla(args);
                case "preview_generated_file" -> previewGeneratedFile(p, args);
                case "update_project_meta" -> updateProjectMeta(p, args);
                case "create_element" -> createElement(p, args);
                case "update_element" -> updateElement(p, args);
                case "delete_element" -> deleteElement(p, args);
                case "create_procedural_texture" -> createProceduralTexture(p, args);
                case "create_3d_model" -> create3dModel(p, args);
                case "create_armor_set" -> createArmorSet(p, args);
                case "create_hud_or_screen" -> createHudOrScreen(p, args);
                case "create_code_file" -> createCodeFile(p, args);
                case "set_translations" -> setTranslations(p, args);
                case "build_or_export" -> buildOrExport(p, args);
                case "get_texture" -> getTexture(p, args);
                case "import_texture" -> importTexture(p, args);
                case "delete_texture" -> deleteTexture(p, args);
                case "rename_element" -> renameElement(p, args);
                case "find_usages" -> findUsages(p, args);
                case "import_artifact" -> importArtifact(p, args);
                case "upgrade_project" -> upgradeProject(p, args);
                case "manage_secrets" -> manageSecrets(p, args);
                case "build_jar" -> buildJar(p, args);
                default -> ToolResult.err("Unknown MCP tool: " + toolName);
            };
        } catch (Exception ex) {
            return ToolResult.err("Tool '" + toolName + "' failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private ToolResult getProjectInfo(Project p) {
        JsonObject o = new JsonObject();
        o.addProperty("name", p.meta.name);
        o.addProperty("modId", p.meta.modId);
        o.addProperty("version", p.meta.version);
        o.addProperty("author", p.meta.authors);
        o.addProperty("description", p.meta.description);
        JsonArray tgs = new JsonArray();
        for (Target t : p.targets()) tgs.add(t.id);
        o.add("targets", tgs);
        JsonArray mcs = new JsonArray();
        for (McVersion v : p.mcVersions()) mcs.add(v.id);
        o.add("mcVersions", mcs);
        JsonArray langs = new JsonArray();
        for (String l : p.meta.languages) langs.add(l);
        o.add("languages", langs);
        JsonObject counts = new JsonObject();
        for (ElementType t : ElementType.values()) {
            long c = p.elements.stream().filter(e -> e.type() == t).count();
            if (c > 0) counts.addProperty(t.id, c);
        }
        o.add("elementCounts", counts);
        o.addProperty("totalElements", p.elements.size());
        o.addProperty("totalTextures", p.textures.size());
        return ToolResult.ok("Project " + p.meta.name + " (" + p.meta.modId + "): " + p.elements.size() + " elements, " + p.textures.size() + " textures.", o, false);
    }

    private ToolResult listElementTypes(Project p) {
        JsonArray arr = new JsonArray();
        for (ElementType t : ElementType.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", t.id);
            o.addProperty("count", p.elements.stream().filter(e -> e.type() == t).count());
            arr.add(o);
        }
        JsonObject data = new JsonObject();
        data.add("types", arr);
        return ToolResult.ok("Listed " + arr.size() + " element types.", data, false);
    }

    private ToolResult listElements(Project p, JsonObject args) {
        String typeFilter = str(args, "type");
        String query = str(args, "query").toLowerCase(Locale.ROOT);
        JsonArray arr = new JsonArray();
        for (Element e : p.elements) {
            if (!typeFilter.isEmpty() && !e.type().id.equals(typeFilter)) continue;
            String sub = e.subtitle();
            if (!query.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(query)
                    && !(sub != null && sub.toLowerCase(Locale.ROOT).contains(query))) continue;
            JsonObject o = new JsonObject();
            o.addProperty("uid", e.uid);
            o.addProperty("type", e.type().id);
            o.addProperty("name", e.name);
            o.addProperty("subtitle", sub == null ? "" : sub);
            arr.add(o);
        }
        JsonObject data = new JsonObject();
        data.add("elements", arr);
        data.addProperty("count", arr.size());
        return ToolResult.ok("Found " + arr.size() + " element(s).", data, false);
    }

    private Element findElement(Project p, JsonObject args) {
        String uid = str(args, "uid");
        if (!uid.isEmpty()) return p.byUid(uid);
        String typeId = str(args, "type");
        String name = str(args, "name");
        if (!typeId.isEmpty() && !name.isEmpty()) {
            ElementType t = ElementType.of(typeId);
            if (t != null) return p.byName(t, name);
        }
        if (!name.isEmpty()) {
            for (Element e : p.elements) if (e.name.equals(name)) return e;
        }
        return null;
    }

    private ToolResult getElement(Project p, JsonObject args) {
        Element e = findElement(p, args);
        if (e == null) return ToolResult.err("Element not found.");
        JsonObject o = ProjectIO.GSON.toJsonTree(e, Element.class).getAsJsonObject();
        return ToolResult.ok("Loaded " + e.type().id + ":" + e.name, o, false);
    }

    private ToolResult listTextures(Project p) {
        JsonArray arr = new JsonArray();
        for (TextureAsset t : p.textures) {
            JsonObject o = new JsonObject();
            o.addProperty("id", t.id);
            o.addProperty("name", t.name);
            o.addProperty("width", t.width);
            o.addProperty("height", t.height);
            o.addProperty("frames", t.frames());
            arr.add(o);
        }
        JsonObject data = new JsonObject();
        data.add("textures", arr);
        return ToolResult.ok("Found " + arr.size() + " texture(s).", data, false);
    }

    private ToolResult validateProject(Project p) {
        List<Validator.Problem> list = Validator.validate(p);
        long errors = Validator.count(list, Validator.Severity.ERROR);
        long warnings = Validator.count(list, Validator.Severity.WARNING);
        long infos = Validator.count(list, Validator.Severity.INFO);
        JsonArray arr = new JsonArray();
        for (Validator.Problem pr : list) {
            JsonObject o = new JsonObject();
            o.addProperty("severity", pr.severity().name());
            o.addProperty("uid", pr.elementUid() == null ? "" : pr.elementUid());
            Element el = pr.elementUid() == null ? null : p.byUid(pr.elementUid());
            if (el != null) o.addProperty("element", el.type().id + ":" + el.name);
            o.addProperty("message", pr.message());
            arr.add(o);
        }
        JsonObject data = new JsonObject();
        data.addProperty("errors", errors);
        data.addProperty("warnings", warnings);
        data.addProperty("infos", infos);
        data.add("problems", arr);
        return ToolResult.ok("Validation: " + errors + " error(s), " + warnings + " warning(s), " + infos + " info(s).", data, false);
    }

    private ToolResult analyzeCompat(Project p) {
        List<Compat.Row> rows = Compat.analyze(p);
        JsonArray arr = new JsonArray();
        for (Compat.Row r : rows) {
            JsonObject cells = new JsonObject();
            boolean anyIssue = false;
            for (var en : r.cells().entrySet()) {
                Compat.Cell c = en.getValue();
                if (c.level() != Compat.Level.FULL || !c.notes().isEmpty()) anyIssue = true;
                JsonObject co = new JsonObject();
                co.addProperty("level", c.level().name());
                JsonArray notes = new JsonArray();
                for (String n : c.notes()) notes.add(n);
                co.add("notes", notes);
                cells.add(en.getKey().id, co);
            }
            if (!anyIssue) continue;
            JsonObject rowObj = new JsonObject();
            rowObj.addProperty("type", r.element().type().id);
            rowObj.addProperty("name", r.element().name);
            rowObj.addProperty("worst", r.worst().name());
            rowObj.add("targets", cells);
            arr.add(rowObj);
        }
        JsonObject data = new JsonObject();
        data.add("notes", arr);
        return ToolResult.ok("Compatibility report: " + arr.size() + " element(s) with fallbacks/notes across targets.", data, false);
    }

    private ToolResult getTranslations(Project p, JsonObject args) {
        String lang = str(args, "lang");
        Map<String, Map<String, String>> files = Localization.files(p);
        JsonObject data = new JsonObject();
        JsonArray langs = new JsonArray();
        for (String l : Localization.languages(p)) langs.add(l);
        data.add("languages", langs);
        JsonObject mapObj = new JsonObject();
        for (var e : files.entrySet()) {
            if (!lang.isEmpty() && !e.getKey().equals(lang)) continue;
            mapObj.add(e.getKey(), ProjectIO.GSON.toJsonTree(e.getValue()));
        }
        data.add("files", mapObj);
        return ToolResult.ok("Returned translations for " + mapObj.size() + " language(s).", data, false);
    }

    private ToolResult searchVanilla(JsonObject args) {
        String kind = str(args, "kind").toLowerCase(Locale.ROOT);
        String q = str(args, "query").toLowerCase(Locale.ROOT);
        List<String> src = switch (kind) {
            case "block", "blocks" -> Vanilla.BLOCKS;
            case "entity", "entities" -> Vanilla.ENTITIES;
            case "effect", "effects" -> Vanilla.EFFECTS;
            case "enchantment", "enchantments" -> Vanilla.ENCHANTMENTS;
            case "sound", "sounds" -> Vanilla.SOUNDS;
            case "particle", "particles" -> Vanilla.PARTICLES;
            case "damage_type", "damage" -> Vanilla.DAMAGE_TYPES;
            case "dimension", "dimensions" -> Vanilla.DIMENSIONS;
            default -> Vanilla.ITEMS;
        };
        JsonArray matches = new JsonArray();
        for (String id : src) {
            if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(id);
                if (matches.size() >= 40) break;
            }
        }
        JsonObject data = new JsonObject();
        data.add("ids", matches);
        return ToolResult.ok("Found " + matches.size() + " vanilla " + (kind.isEmpty() ? "item" : kind) + " id(s).", data, false);
    }

    private ToolResult previewGeneratedFile(Project p, JsonObject args) {
        Target t = Target.of(str(args, "target"));
        if (t == null) t = p.targets().get(0);
        McVersion v = McVersion.orNull(str(args, "mcVersion"));
        if (v == null) v = p.mc();
        GenContext ctx = Generators.generate(p, t, v);
        String path = str(args, "path");
        JsonObject data = new JsonObject();
        data.addProperty("target", t.id);
        data.addProperty("mcVersion", v.id);
        if (path.isEmpty()) {
            JsonArray list = new JsonArray();
            for (String fp : ctx.files.paths()) list.add(fp);
            data.add("files", list);
            return ToolResult.ok("Generated " + list.size() + " file(s) for " + t.id + " (" + v.id + ").", data, false);
        }
        byte[] bytes = ctx.files.get(path);
        if (bytes == null) return ToolResult.err("Generated path not found: " + path);
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        data.addProperty("path", path);
        data.addProperty("content", text);
        return ToolResult.ok(text, data, false);
    }

    private ToolResult updateProjectMeta(Project p, JsonObject args) {
        if (args.has("name")) p.meta.name = args.get("name").getAsString();
        if (args.has("modId")) {
            String newId = Ids.toId(args.get("modId").getAsString());
            if (!newId.isEmpty() && !newId.equals(p.meta.modId)) {
                Refactor.renameNamespace(p, p.meta.modId, newId);
                p.meta.modId = newId;
            }
        }
        if (args.has("version")) p.meta.version = args.get("version").getAsString();
        if (args.has("authors")) p.meta.authors = args.get("authors").getAsString();
        else if (args.has("author")) p.meta.authors = args.get("author").getAsString();
        if (args.has("description")) p.meta.description = args.get("description").getAsString();
        if (args.has("packageName")) p.meta.packageName = args.get("packageName").getAsString();
        if (args.has("license")) p.meta.license = args.get("license").getAsString();
        if (args.has("website")) p.meta.website = args.get("website").getAsString();
        if (args.has("customGradle")) p.meta.customGradle = args.get("customGradle").getAsString();
        if (args.has("jvmArgs")) p.meta.jvmArgs = args.get("jvmArgs").getAsString();
        if (args.has("targets") && args.get("targets").isJsonArray()) {
            List<String> list = new ArrayList<>();
            for (JsonElement el : args.getAsJsonArray("targets")) {
                Target t = Target.of(el.getAsString());
                if (t != null && !list.contains(t.id)) list.add(t.id);
            }
            if (!list.isEmpty()) p.meta.targets = list;
        }
        if (args.has("mcVersions") && args.get("mcVersions").isJsonArray()) {
            List<String> list = new ArrayList<>();
            for (JsonElement el : args.getAsJsonArray("mcVersions")) {
                McVersion mv = McVersion.orNull(el.getAsString());
                if (mv != null && !list.contains(mv.id)) list.add(mv.id);
            }
            if (!list.isEmpty()) {
                p.meta.mcVersions = list;
                p.meta.mcVersion = list.get(0);
                p.normalizeVersions();
            }
        }
        if (args.has("languages") && args.get("languages").isJsonArray()) {
            LinkedHashSet<String> set = new LinkedHashSet<>(p.meta.languages);
            for (JsonElement el : args.getAsJsonArray("languages")) {
                String l = Ids.toId(el.getAsString());
                if (!l.isEmpty()) set.add(l);
            }
            p.meta.languages = new ArrayList<>(set);
        }
        return ToolResult.ok("Updated project metadata.", getProjectInfo(p).data(), true);
    }

    private ToolResult createElement(Project p, JsonObject args) {
        ElementType type = ElementType.of(str(args, "type"));
        if (type == null) return ToolResult.err("Unknown element type: " + str(args, "type"));
        String cleanName = sanitizeName(type, str(args, "name"));
        if (cleanName.isEmpty()) return ToolResult.err("Invalid element name.");
        cleanName = uniqueName(p, type, cleanName);

        Element el = type.create();
        el.name = cleanName;
        ElementDefaults.init(p, el);
        if (args.has("fields") && args.get("fields").isJsonObject()) {
            el = patchElement(el, args.getAsJsonObject("fields"));
            el.name = cleanName;
        }
        p.elements.add(el);
        ProjectIO.migrate(p);
        return ToolResult.ok("Created " + type.id + ":" + el.name + " (uid=" + el.uid + ").",
                ProjectIO.GSON.toJsonTree(el, Element.class).getAsJsonObject(), true);
    }

    private ToolResult updateElement(Project p, JsonObject args) {
        Element el = findElement(p, args);
        if (el == null) return ToolResult.err("Element not found.");
        int idx = p.elements.indexOf(el);
        String oldName = el.name;
        JsonObject fields = args.has("fields") && args.get("fields").isJsonObject() ? args.getAsJsonObject("fields") : new JsonObject();
        Element updated = patchElement(el, fields);
        if (fields.has("name")) {
            String renamed = sanitizeName(el.type(), fields.get("name").getAsString());
            if (!renamed.isEmpty() && !renamed.equals(oldName)) {
                updated.name = renamed;
                Refactor.renameElement(p, updated, oldName, renamed);
            }
        }
        p.elements.set(idx, updated);
        ProjectIO.migrate(p);
        return ToolResult.ok("Updated " + updated.type().id + ":" + updated.name + ".",
                ProjectIO.GSON.toJsonTree(updated, Element.class).getAsJsonObject(), true);
    }

    private ToolResult deleteElement(Project p, JsonObject args) {
        Element el = findElement(p, args);
        if (el == null) return ToolResult.err("Element not found.");
        p.elements.remove(el);
        JsonObject o = new JsonObject();
        o.addProperty("deletedUid", el.uid);
        o.addProperty("type", el.type().id);
        o.addProperty("name", el.name);
        return ToolResult.ok("Deleted " + el.type().id + ":" + el.name + ".", o, true);
    }

    private ToolResult createProceduralTexture(Project p, JsonObject args) {
        String name = Ids.toId(str(args, "name"));
        if (name.isEmpty()) name = "generated_tex";
        String style = str(args, "style").toLowerCase(Locale.ROOT);
        int size = args.has("size") ? Math.max(16, Math.min(64, args.get("size").getAsInt())) : 16;
        int c1 = parseHexRgb(str(args, "primaryColor"), 0x3B82F6);
        int c2 = parseHexRgb(str(args, "secondaryColor"), style.equals("ore") ? 0x7D7D7D : 0x6B4423);

        int[] px = drawProcedural(style, size, c1, c2);
        TextureAsset ta = TextureAsset.of(name, px, size, size);
        p.textures.add(ta);

        String assignType = str(args, "assignToType");
        String assignName = str(args, "assignToName");
        if (!assignType.isEmpty() && !assignName.isEmpty()) {
            ElementType et = ElementType.of(assignType);
            Element target = et == null ? null : p.byName(et, assignName);
            switch (target) {
                case Item it -> it.texture = ta.id;
                case Block bl -> bl.texture = ta.id;
                case CustomEntity ce -> ce.texture = ta.id;
                case Model md -> md.textures.put("0", ta.id);
                case null, default -> {}
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("id", ta.id);
        o.addProperty("name", ta.name);
        o.addProperty("width", size);
        o.addProperty("height", size);
        return ToolResult.ok("Created procedural texture '" + ta.name + "' (" + size + "x" + size + ", id=" + ta.id + ").", o, true);
    }

    private ToolResult create3dModel(Project p, JsonObject args) {
        String name = Ids.toId(str(args, "name"));
        if (name.isEmpty()) return ToolResult.err("Model name is required.");
        Model existing = p.model(name);
        Model m = existing != null ? existing : new Model();
        m.name = name;
        String template = str(args, "template");
        if (!template.isEmpty()) Models.applyTemplate(m, template);
        if (args.has("kind")) m.kind = args.get("kind").getAsString();
        if (args.has("textureWidth")) m.textureWidth = Math.max(1, args.get("textureWidth").getAsInt());
        if (args.has("textureHeight")) m.textureHeight = Math.max(1, args.get("textureHeight").getAsInt());
        if (args.has("bones") && args.get("bones").isJsonArray()) {
            m.bones.clear();
            for (JsonElement be : args.getAsJsonArray("bones")) {
                Bone b = ProjectIO.GSON.fromJson(be, Bone.class);
                if (b != null) m.bones.add(b);
            }
        }
        if (args.has("cubes") && args.get("cubes").isJsonArray()) {
            m.cubes.clear();
            for (JsonElement ce : args.getAsJsonArray("cubes")) {
                Cube c = ProjectIO.GSON.fromJson(ce, Cube.class);
                if (c != null) m.cubes.add(c);
            }
        }
        if (m.textures.isEmpty()) m.textures.put("0", "");
        if (existing == null) p.elements.add(m);
        return ToolResult.ok((existing == null ? "Created" : "Updated") + " 3D model '" + m.name + "' (" + m.cubes.size() + " cubes, " + m.bones.size() + " bones).",
                ProjectIO.GSON.toJsonTree(m, Element.class).getAsJsonObject(), true);
    }

    private ToolResult createArmorSet(Project p, JsonObject args) {
        String base = Ids.toId(str(args, "name"));
        if (base.isEmpty()) return ToolResult.err("Base name required.");
        String label = args.has("displayNamePrefix") ? args.get("displayNamePrefix").getAsString() : Ids.titleCase(base);
        int hex = parseHexRgb(str(args, "color"), 0x4EA8DE);
        boolean make3d = args.has("create3dModels") && args.get("create3dModels").getAsBoolean();

        ArmorMaterial am = p.byName(ElementType.ARMOR_MATERIAL, base) instanceof ArmorMaterial existing ? existing : null;
        if (am == null) {
            am = new ArmorMaterial();
            am.name = base;
            p.elements.add(am);
        }
        am.durability = args.has("durability") ? Math.max(1, args.get("durability").getAsInt()) : 28;
        am.helmet = args.has("helmetDefense") ? args.get("helmetDefense").getAsInt() : 3;
        am.chestplate = args.has("chestDefense") ? args.get("chestDefense").getAsInt() : 8;
        am.leggings = args.has("legsDefense") ? args.get("legsDefense").getAsInt() : 6;
        am.boots = args.has("bootsDefense") ? args.get("bootsDefense").getAsInt() : 3;
        if (args.has("toughness")) am.toughness = args.get("toughness").getAsDouble();
        if (args.has("setBonusEffect")) am.setBonusEffect = args.get("setBonusEffect").getAsString();

        // Layer / 3D armor texture
        TextureAsset armorTex = TextureAsset.of(base + "_armor_3d", drawProcedural("block", 64, hex, darken(hex, 0.7)), 64, 64);
        p.textures.add(armorTex);
        am.layer1 = armorTex.id;
        am.layer2 = armorTex.id;
        am.armorTexture = armorTex.id;

        String[][] slots = {
                {"helmet", "Helmet", "armor_helmet"},
                {"chestplate", "Chestplate", "armor_chestplate"},
                {"leggings", "Leggings", "armor_leggings"},
                {"boots", "Boots", "armor_boots"}
        };
        JsonArray created = new JsonArray();
        for (String[] sl : slots) {
            String itemName = base + "_" + sl[0];
            if (p.itemLike(p.meta.modId + ":" + itemName) == null) {
                Item it = new Item();
                it.name = itemName;
                it.displayName = label + " " + sl[1];
                it.kind = sl[0];
                it.armorMaterial = am.name;
                TextureAsset icon = TextureAsset.of(itemName, drawProcedural("armor", 16, hex, darken(hex, 0.65)), 16, 16);
                p.textures.add(icon);
                it.texture = icon.id;
                if (make3d) {
                    String mName = itemName + "_3d";
                    Model mdl = new Model();
                    mdl.name = mName;
                    Models.applyTemplate(mdl, sl[2]);
                    mdl.textures.put("0", armorTex.id);
                    p.elements.add(mdl);
                    switch (sl[0]) {
                        case "helmet" -> am.helmetModel = mName;
                        case "chestplate" -> am.chestplateModel = mName;
                        case "leggings" -> am.leggingsModel = mName;
                        case "boots" -> am.bootsModel = mName;
                    }
                    it.wornModel = mName;
                }
                p.elements.add(it);
                created.add(itemName);
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("armorMaterial", am.name);
        o.addProperty("models3d", make3d);
        o.add("items", created);
        return ToolResult.ok("Created armor set '" + am.name + "' (3D=" + make3d + ") with items " + created + ".", o, true);
    }

    private ToolResult createHudOrScreen(Project p, JsonObject args) {
        String kind = str(args, "kind").toLowerCase(Locale.ROOT);
        String name = Ids.toId(str(args, "name"));
        if (name.isEmpty()) return ToolResult.err("Element name required.");
        JsonObject trs = args.has("translations") && args.get("translations").isJsonObject() ? args.getAsJsonObject("translations") : null;
        if (trs != null) {
            for (String lang : trs.keySet()) {
                if (!p.meta.languages.contains(lang)) p.meta.languages.add(lang);
            }
        }
        if ("screen".equals(kind)) {
            Systems.CustomScreen sc = p.byName(ElementType.SCREEN, name) instanceof Systems.CustomScreen existing ? existing : null;
            if (sc == null) {
                sc = new Systems.CustomScreen();
                sc.name = name;
                p.elements.add(sc);
            }
            if (args.has("mode")) sc.mode = args.get("mode").getAsString();
            if (args.has("title")) sc.title = args.get("title").getAsString();
            if (args.has("widgets") && args.get("widgets").isJsonArray()) {
                sc.widgets.clear();
                for (JsonElement we : args.getAsJsonArray("widgets")) {
                    Systems.ScreenWidget w = ProjectIO.GSON.fromJson(we, Systems.ScreenWidget.class);
                    if (w != null) sc.widgets.add(w);
                }
            }
            if (trs != null) {
                for (var en : trs.entrySet()) {
                    if (!en.getValue().isJsonObject()) continue;
                    JsonObject obj = en.getValue().getAsJsonObject();
                    String lang = en.getKey();
                    if (obj.has("title")) sc.setLoc("title", lang, obj.get("title").getAsString());
                    if (obj.has("widgets") && obj.get("widgets").isJsonArray()) {
                        JsonArray arr = obj.getAsJsonArray("widgets");
                        for (int i = 0; i < arr.size() && i < sc.widgets.size(); i++) {
                            if (!arr.get(i).isJsonObject()) continue;
                            JsonObject wo = arr.get(i).getAsJsonObject();
                            for (String f : List.of("text", "tooltip", "placeholder", "options")) {
                                if (wo.has(f)) sc.setLoc("w." + i + "." + f, lang, wo.get(f).getAsString());
                            }
                        }
                    }
                }
            }
            return ToolResult.ok("Saved CustomScreen '" + sc.name + "' (" + sc.mode + ", " + sc.widgets.size() + " widgets).",
                    ProjectIO.GSON.toJsonTree(sc, Element.class).getAsJsonObject(), true);
        }

        Systems.Hud h = p.byName(ElementType.HUD, name) instanceof Systems.Hud existing ? existing : null;
        if (h == null) {
            h = new Systems.Hud();
            h.name = name;
            p.elements.add(h);
        }
        if (args.has("mode")) h.kind = args.get("mode").getAsString();
        if (args.has("title")) h.title = args.get("title").getAsString();
        if (args.has("lines") && args.get("lines").isJsonArray()) {
            h.lines.clear();
            for (JsonElement le : args.getAsJsonArray("lines")) h.lines.add(le.getAsString());
        }
        if (trs != null) {
            for (var en : trs.entrySet()) {
                if (!en.getValue().isJsonObject()) continue;
                JsonObject obj = en.getValue().getAsJsonObject();
                String lang = en.getKey();
                if (obj.has("title")) h.setLoc("title", lang, obj.get("title").getAsString());
                if (obj.has("lines") && obj.get("lines").isJsonArray()) {
                    JsonArray arr = obj.getAsJsonArray("lines");
                    for (int i = 0; i < arr.size(); i++) h.setLoc("line." + i, lang, arr.get(i).getAsString());
                }
            }
        }
        return ToolResult.ok("Saved HUD '" + h.name + "' (" + h.kind + ", " + h.lines.size() + " lines).",
                ProjectIO.GSON.toJsonTree(h, Element.class).getAsJsonObject(), true);
    }

    private ToolResult createCodeFile(Project p, JsonObject args) {
        String name = Ids.toId(str(args, "name"));
        if (name.isEmpty()) return ToolResult.err("CodeFile name required.");
        String lang = str(args, "language").isEmpty() ? "auto" : str(args, "language").toLowerCase(Locale.ROOT);
        CodeFile cf = p.byName(ElementType.CODE_FILE, name) instanceof CodeFile existing ? existing : null;
        if (cf == null) {
            cf = new CodeFile();
            cf.name = name;
            p.elements.add(cf);
        }
        if (!str(args, "target").isEmpty()) cf.target = str(args, "target");
        cf.language = lang;
        if (!str(args, "path").isEmpty()) cf.path = str(args, "path");
        else {
            String pas = Ids.toPascal(name);
            String rl = cf.resolvedLanguage();
            cf.path = switch (rl) {
                case "kotlin" -> "src/main/kotlin/{package}/custom/" + pas + ".kt";
                case "scala" -> "src/main/scala/{package}/custom/" + pas + ".scala";
                case "groovy" -> "src/main/groovy/{package}/custom/" + pas + ".groovy";
                case "typescript" -> "scripts/" + name + ".ts";
                case "javascript" -> "scripts/" + name + ".js";
                case "python" -> "scripts/" + name + ".py";
                case "lua" -> "scripts/" + name + ".lua";
                case "toml" -> "src/main/resources/" + name + ".toml";
                case "yaml" -> "src/main/resources/" + name + ".yml";
                case "mcfunction" -> "data/{modid}/function/" + name + ".mcfunction";
                default -> "src/main/java/{package}/custom/" + pas + ".java";
            };
        }
        if (!str(args, "hook").isEmpty()) cf.hook = str(args, "hook");
        if (!str(args, "hookMethod").isEmpty()) cf.hookMethod = str(args, "hookMethod");
        cf.content = args.has("content") ? args.get("content").getAsString() : "";
        return ToolResult.ok("Saved CodeFile '" + cf.name + "' (" + cf.resolvedLanguage() + " -> " + cf.path + ").",
                ProjectIO.GSON.toJsonTree(cf, Element.class).getAsJsonObject(), true);
    }

    private ToolResult setTranslations(Project p, JsonObject args) {
        if (args.has("languages") && args.get("languages").isJsonArray()) {
            for (JsonElement le : args.getAsJsonArray("languages")) {
                String l = le.getAsString().trim();
                if (!l.isEmpty() && !p.meta.languages.contains(l)) p.meta.languages.add(l);
            }
        }
        int updated = 0;
        if (args.has("entries") && args.get("entries").isJsonArray()) {
            for (JsonElement ee : args.getAsJsonArray("entries")) {
                if (!ee.isJsonObject()) continue;
                JsonObject eo = ee.getAsJsonObject();
                String lang = str(eo, "lang");
                String val = eo.has("value") ? eo.get("value").getAsString() : "";
                if (lang.isEmpty()) continue;
                if (!p.meta.languages.contains(lang)) p.meta.languages.add(lang);
                if (eo.has("type") && eo.has("name")) {
                    ElementType et = ElementType.of(str(eo, "type"));
                    Element el = et == null ? null : p.byName(et, str(eo, "name"));
                    String field = str(eo, "field").isEmpty() ? "name" : str(eo, "field");
                    if (el != null) {
                        el.setLoc(field, lang, val);
                        updated++;
                    }
                } else if (eo.has("key")) {
                    p.translations.computeIfAbsent(lang, k -> new LinkedHashMap<>()).put(str(eo, "key"), val);
                    updated++;
                }
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("updated", updated);
        return ToolResult.ok("Updated " + updated + " translation entry/entries.", o, updated > 0);
    }

    private ToolResult buildOrExport(Project p, JsonObject args) throws IOException {
        Path out = Path.of(str(args, "outputDir"));
        String singleTarget = str(args, "target");
        McVersion v = McVersion.orNull(str(args, "mcVersion"));
        if (v == null) v = p.mc();
        int totalFiles = 0;
        JsonArray targetsOut = new JsonArray();
        for (Target t : p.targets()) {
            if (!singleTarget.isEmpty() && !t.id.equals(singleTarget)) continue;
            GenContext ctx = Generators.generate(p, t, v);
            Path targetDir = out.resolve(t.id + "-" + v.id);
            Exporter.writeFolder(ctx.files, targetDir);
            totalFiles += ctx.files.size();
            JsonObject to = new JsonObject();
            to.addProperty("target", t.id);
            to.addProperty("path", targetDir.toAbsolutePath().toString());
            to.addProperty("files", ctx.files.size());
            targetsOut.add(to);
        }
        JsonObject data = new JsonObject();
        data.addProperty("totalFiles", totalFiles);
        data.add("outputs", targetsOut);
        return ToolResult.ok("Exported " + totalFiles + " file(s) to " + out.toAbsolutePath() + ".", data, false);
    }

    private ToolResult getTexture(Project p, JsonObject args) {
        String id = str(args, "id");
        String name = str(args, "name");
        TextureAsset ta = !id.isEmpty() ? p.texture(id) : null;
        if (ta == null && !name.isEmpty()) {
            for (TextureAsset t : p.textures) if (t.name.equals(name)) { ta = t; break; }
        }
        if (ta == null) return ToolResult.err("Texture not found.");
        JsonObject o = new JsonObject();
        o.addProperty("id", ta.id);
        o.addProperty("name", ta.name);
        o.addProperty("width", ta.width);
        o.addProperty("height", ta.height);
        o.addProperty("frames", ta.frames());
        o.addProperty("frameTime", ta.frameTime);
        o.addProperty("interpolate", ta.interpolate);
        o.addProperty("base64", ta.png == null ? "" : ta.png);
        return ToolResult.ok("Loaded texture '" + ta.name + "' (" + ta.width + "x" + ta.height + ").", o, false);
    }

    private ToolResult importTexture(Project p, JsonObject args) throws IOException {
        String name = Ids.toId(str(args, "name"));
        if (name.isEmpty()) return ToolResult.err("Texture name required.");
        String pathStr = str(args, "path");
        String b64 = str(args, "base64");
        byte[] bytes;
        if (!pathStr.isEmpty()) {
            Path path = Path.of(pathStr);
            if (!Files.isRegularFile(path)) return ToolResult.err("Texture file not found: " + path);
            bytes = Files.readAllBytes(path);
        } else if (!b64.isEmpty()) {
            bytes = Base64.getDecoder().decode(b64);
        } else {
            return ToolResult.err("Either path or base64 is required.");
        }
        int[] wh = new int[2];
        int[] px = Images.read(bytes, wh);
        if (px == null || wh[0] <= 0 || wh[1] <= 0) return ToolResult.err("Invalid PNG image data.");
        TextureAsset ta = TextureAsset.of(name, px, wh[0], wh[1]);
        p.textures.add(ta);
        String assignType = str(args, "assignToType");
        String assignName = str(args, "assignToName");
        if (!assignType.isEmpty() && !assignName.isEmpty()) {
            ElementType et = ElementType.of(assignType);
            Element target = et == null ? null : p.byName(et, assignName);
            switch (target) {
                case Item it -> it.texture = ta.id;
                case Block bl -> bl.texture = ta.id;
                case CustomEntity ce -> ce.texture = ta.id;
                case Model md -> md.textures.put("0", ta.id);
                case null, default -> {}
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("id", ta.id);
        o.addProperty("name", ta.name);
        o.addProperty("width", ta.width);
        o.addProperty("height", ta.height);
        return ToolResult.ok("Imported texture '" + ta.name + "' (" + ta.width + "x" + ta.height + ", id=" + ta.id + ").", o, true);
    }

    private ToolResult deleteTexture(Project p, JsonObject args) {
        String id = str(args, "id");
        String name = str(args, "name");
        TextureAsset ta = !id.isEmpty() ? p.texture(id) : null;
        if (ta == null && !name.isEmpty()) {
            for (TextureAsset t : p.textures) if (t.name.equals(name)) { ta = t; break; }
        }
        if (ta == null) return ToolResult.err("Texture not found.");
        p.textures.remove(ta);
        JsonObject o = new JsonObject();
        o.addProperty("deletedId", ta.id);
        o.addProperty("name", ta.name);
        return ToolResult.ok("Deleted texture '" + ta.name + "'.", o, true);
    }

    private ToolResult renameElement(Project p, JsonObject args) {
        Element el = findElement(p, args);
        if (el == null) return ToolResult.err("Element not found.");
        String newName = sanitizeName(el.type(), str(args, "newName"));
        if (newName.isEmpty()) return ToolResult.err("Invalid newName.");
        if (newName.equals(el.name)) return ToolResult.ok("Element already named '" + newName + "'.", ProjectIO.GSON.toJsonTree(el, Element.class).getAsJsonObject(), false);
        if (existsInGroup(p, el.type(), newName)) return ToolResult.err("Another element in the same registry group already uses '" + newName + "'.");
        String oldName = el.name;
        el.name = newName;
        Refactor.renameElement(p, el, oldName, newName);
        return ToolResult.ok("Renamed " + el.type().id + ":" + oldName + " -> " + newName + " with full reference refactoring.",
                ProjectIO.GSON.toJsonTree(el, Element.class).getAsJsonObject(), true);
    }

    private ToolResult findUsages(Project p, JsonObject args) {
        String raw = str(args, "target");
        if (raw.isEmpty()) return ToolResult.err("target parameter required.");
        String full = Ids.full(raw.contains(":") ? raw : p.meta.modId + ":" + raw);
        String bare = raw.contains(":") ? Ids.path(raw) : raw;
        JsonArray arr = new JsonArray();
        for (Element el : p.elements) {
            if (el.name.equals(bare) || el.uid.equals(raw)) continue;
            String json = ProjectIO.GSON.toJson(el);
            if (json.contains("\"" + raw + "\"") || json.contains("\"" + full + "\"") || json.contains("\"" + bare + "\"")) {
                JsonObject o = new JsonObject();
                o.addProperty("uid", el.uid);
                o.addProperty("type", el.type().id);
                o.addProperty("name", el.name);
                arr.add(o);
            }
        }
        JsonObject data = new JsonObject();
        data.addProperty("target", raw);
        data.add("usages", arr);
        data.addProperty("count", arr.size());
        return ToolResult.ok("Found " + arr.size() + " element(s) referencing '" + raw + "'.", data, false);
    }

    private ToolResult importArtifact(Project p, JsonObject args) throws IOException {
        Path path = Path.of(str(args, "path"));
        if (!Files.exists(path)) return ToolResult.err("Artifact file not found: " + path);
        boolean overwrite = args.has("overwrite") && args.get("overwrite").getAsBoolean();
        ProjectImporter.Report res = ProjectImporter.importArtifact(path);
        int addedEl = 0, addedTex = 0;
        for (TextureAsset ta : res.project().textures) {
            if (p.texture(ta.id) == null && p.textures.stream().noneMatch(x -> x.name.equals(ta.name))) {
                p.textures.add(ta);
                addedTex++;
            }
        }
        for (Element el : res.project().elements) {
            Element existing = p.byName(el.type(), el.name);
            if (existing != null) {
                if (overwrite) {
                    p.elements.remove(existing);
                    p.elements.add(el);
                    addedEl++;
                }
            } else {
                p.elements.add(el);
                addedEl++;
            }
        }
        ProjectIO.migrate(p);
        JsonObject data = new JsonObject();
        data.addProperty("importedElements", addedEl);
        data.addProperty("importedTextures", addedTex);
        JsonArray notes = new JsonArray();
        for (String n : res.notes()) notes.add(n);
        data.add("notes", notes);
        return ToolResult.ok("Imported " + addedEl + " element(s) and " + addedTex + " texture(s) from " + path.getFileName() + ".", data, addedEl > 0 || addedTex > 0);
    }

    private ToolResult upgradeProject(Project p, JsonObject args) {
        McVersion targetMc = McVersion.orNull(str(args, "mcVersion"));
        if (targetMc == null) return ToolResult.err("Unknown mcVersion: " + str(args, "mcVersion"));
        ProjectUpgrader.Report rep = ProjectUpgrader.upgrade(p, targetMc, p.targets());
        JsonObject data = new JsonObject();
        data.addProperty("targetMc", targetMc.id);
        JsonArray ch = new JsonArray();
        for (String c : rep.changes()) ch.add(c);
        JsonArray wr = new JsonArray();
        for (String w : rep.warnings()) wr.add(w);
        data.add("changes", ch);
        data.add("warnings", wr);
        return ToolResult.ok("Upgraded project to " + targetMc.id + ": " + ch.size() + " change(s), " + wr.size() + " warning(s).", data, true);
    }

    private ToolResult manageSecrets(Project p, JsonObject args) throws IOException {
        String action = str(args, "action").isEmpty() ? "list" : str(args, "action").toLowerCase(Locale.ROOT);
        String dirStr = str(args, "projectDir");
        Path dir = !dirStr.isEmpty() ? Path.of(dirStr) : projectDir;
        if ("list".equals(action)) {
            JsonObject data = new JsonObject();
            JsonArray refs = new JsonArray();
            for (String s : Secrets.referenced(p)) refs.add(s);
            data.add("referencedKeys", refs);
            JsonArray stored = new JsonArray();
            if (dir != null) {
                Secrets sec = new Secrets(dir);
                for (var e : sec.all().entrySet()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", e.getKey());
                    o.addProperty("hasValue", e.getValue() != null && !e.getValue().isBlank());
                    stored.add(o);
                }
            }
            data.add("storedKeys", stored);
            return ToolResult.ok("Listed " + refs.size() + " referenced and " + stored.size() + " stored secret key(s).", data, false);
        }
        if (dir == null) return ToolResult.err("projectDir is required to store or remove secrets.");
        String key = str(args, "name");
        if (key.isEmpty()) return ToolResult.err("Secret name required.");
        Secrets sec = new Secrets(dir);
        if ("set".equals(action)) {
            sec.put(key, args.has("value") ? args.get("value").getAsString() : "");
            sec.save();
            JsonObject data = new JsonObject();
            data.addProperty("name", key);
            return ToolResult.ok("Saved secret '" + key + "' locally.", data, false);
        }
        if ("remove".equals(action)) {
            sec.remove(key);
            sec.save();
            JsonObject data = new JsonObject();
            data.addProperty("removed", key);
            return ToolResult.ok("Removed secret '" + key + "'.", data, false);
        }
        return ToolResult.err("Unknown manage_secrets action: " + action);
    }

    private ToolResult buildJar(Project p, JsonObject args) throws IOException {
        Target t = Target.of(str(args, "target"));
        if (t == null) {
            t = p.targets().stream().filter(Target::usesGradle).findFirst().orElse(null);
        }
        if (t == null || !t.usesGradle()) return ToolResult.err("No Gradle target found in project.");
        McVersion v = McVersion.orNull(str(args, "mcVersion"));
        if (v == null) v = p.mc();
        String outStr = str(args, "outputDir");
        Path workDir = outStr.isEmpty() ? Files.createTempDirectory("modstudio-build-" + t.id + "-") : Path.of(outStr).resolve(t.id + "-" + v.id);
        Path javaHome = BuildService.findJavaHome(str(args, "javaHome"));
        if (javaHome == null) return ToolResult.err("No JDK (with javac) found. Pass javaHome or configure JAVA_HOME.");
        GenContext ctx = Generators.generate(p, t, v);
        Exporter.writeFolder(ctx.files, workDir);
        List<String> logs = new ArrayList<>();
        BuildService.run(workDir, javaHome, List.of("build"), List.of(), logs::add, null);
        Path jar = BuildService.findJar(workDir);
        JsonObject data = new JsonObject();
        data.addProperty("target", t.id);
        data.addProperty("mcVersion", v.id);
        data.addProperty("buildDir", workDir.toAbsolutePath().toString());
        data.addProperty("jar", jar == null ? "" : jar.toAbsolutePath().toString());
        return ToolResult.ok("Built " + t.id + " (" + v.id + ") -> " + (jar != null ? jar.toAbsolutePath() : workDir), data, false);
    }

    // ------------------------------------------------------------------ helpers

    private static Element patchElement(Element el, JsonObject patch) {
        JsonObject merged = ProjectIO.GSON.toJsonTree(el, Element.class).getAsJsonObject();
        for (var e : patch.entrySet()) {
            String k = e.getKey();
            if ("uid".equals(k) || "type".equals(k)) continue;
            merged.add(k, e.getValue());
        }
        Element out = ProjectIO.GSON.fromJson(merged, Element.class);
        out.uid = el.uid;
        return out;
    }

    private static String sanitizeName(ElementType type, String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (type.allowsPath()) return s.replaceAll("[^a-z0-9_./-]", "_").replaceAll("^/+|/+$", "");
        return Ids.toId(s);
    }

    private static String uniqueName(Project p, ElementType type, String desired) {
        String base = desired;
        int n = 2;
        while (existsInGroup(p, type, desired)) desired = base + "_" + (n++);
        return desired;
    }

    private static boolean existsInGroup(Project p, ElementType type, String name) {
        String g = type.registryGroup();
        for (Element e : p.elements) {
            if (e.type().registryGroup().equals(g) && e.name.equals(name)) return true;
        }
        return false;
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().trim() : "";
    }

    private static int parseHexRgb(String hex, int def) {
        if (hex == null || hex.isEmpty()) return def;
        try {
            return Integer.parseInt(hex.replace("#", "").trim(), 16) & 0xFFFFFF;
        } catch (Exception e) {
            return def;
        }
    }

    private static int darken(int rgb, double k) {
        int r = Math.max(0, Math.min(255, (int) Math.round(((rgb >> 16) & 255) * k)));
        int g = Math.max(0, Math.min(255, (int) Math.round(((rgb >> 8) & 255) * k)));
        int b = Math.max(0, Math.min(255, (int) Math.round((rgb & 255) * k)));
        return (r << 16) | (g << 8) | b;
    }

    private static int lighten(int rgb, double k) {
        int r = Math.min(255, (int) Math.round(((rgb >> 16) & 255) + 255 * k));
        int g = Math.min(255, (int) Math.round(((rgb >> 8) & 255) + 255 * k));
        int b = Math.min(255, (int) Math.round((rgb & 255) + 255 * k));
        return (r << 16) | (g << 8) | b;
    }

    public static int[] drawProcedural(String style, int size, int c1, int c2) {
        int[] px = new int[size * size];
        int dark = 0xFF000000 | darken(c1, 0.58);
        int mid = 0xFF000000 | c1;
        int light = 0xFF000000 | lighten(c1, 0.25);
        int stone = 0xFF000000 | c2;
        int stoneDark = 0xFF000000 | darken(c2, 0.82);
        Random rng = new Random(style.hashCode() * 31L + c1);

        int[] base16 = new int[256];
        switch (style) {
            case "ore" -> {
                for (int i = 0; i < 256; i++) base16[i] = rng.nextInt(5) == 0 ? stoneDark : stone;
                int[] flecks = {3*16+4, 3*16+5, 4*16+4, 4*16+10, 5*16+10, 5*16+11, 8*16+6, 9*16+6, 9*16+7, 11*16+3, 12*16+3, 11*16+11, 12*16+11, 12*16+12};
                for (int f : flecks) base16[f] = (f % 2 == 0) ? light : mid;
            }
            case "brick" -> {
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    boolean mortar = y % 4 == 0 || ((x + (y / 4 % 2) * 4) % 8 == 0);
                    base16[y * 16 + x] = mortar ? dark : (rng.nextInt(4) == 0 ? 0xFF000000 | darken(c1, 0.88) : mid);
                }
            }
            case "gem" -> {
                for (int y = 2; y < 14; y++) for (int x = 3; x < 13; x++) {
                    int dist = Math.abs(x - 8) + Math.abs(y - 8);
                    if (dist <= 6) base16[y * 16 + x] = dist >= 5 ? dark : (x < 8 && y < 8 ? light : mid);
                }
            }
            case "ingot" -> {
                for (int y = 5; y <= 11; y++) for (int x = 2 + (y == 5 || y == 11 ? 1 : 0); x <= 13 - (y == 5 || y == 11 ? 1 : 0); x++) {
                    base16[y * 16 + x] = (y == 5 || y == 11 || x == 2 || x == 13) ? dark : (y <= 7 ? light : mid);
                }
            }
            case "sword" -> {
                for (int i = 4; i <= 13; i++) {
                    base16[(15 - i) * 16 + i] = light;
                    base16[(15 - i) * 16 + Math.min(15, i + 1)] = mid;
                    base16[Math.max(0, 14 - i) * 16 + i] = mid;
                }
                for (int d = -2; d <= 2; d++) {
                    int x = 5 + d, y = 10 + d;
                    if (x >= 0 && x < 16 && y >= 0 && y < 16) base16[y * 16 + x] = dark;
                }
                base16[12 * 16 + 3] = stone;
                base16[13 * 16 + 2] = stoneDark;
            }
            case "pickaxe" -> {
                for (int i = 2; i <= 12; i++) base16[(15 - i) * 16 + i] = stone;
                for (int k = 4; k <= 12; k++) {
                    base16[2 * 16 + k] = light;
                    base16[3 * 16 + k] = mid;
                }
                base16[4 * 16 + 13] = mid;
                base16[5 * 16 + 13] = dark;
            }
            case "axe" -> {
                for (int i = 2; i <= 12; i++) base16[(15 - i) * 16 + i] = stone;
                for (int y = 2; y <= 6; y++) for (int x = 8; x <= 13; x++) {
                    if (y + x <= 18) base16[y * 16 + x] = (x == 13 || y == 6) ? dark : (y <= 3 ? light : mid);
                }
            }
            case "armor" -> {
                for (int y = 3; y <= 13; y++) for (int x = 3; x <= 12; x++) {
                    if (y <= 5 && x >= 6 && x <= 9) continue;
                    boolean border = y == 3 || y == 13 || x == 3 || x == 12 || (y == 6 && x >= 5 && x <= 10);
                    base16[y * 16 + x] = border ? dark : (x < 7 ? light : mid);
                }
            }
            case "potion" -> {
                for (int x = 6; x <= 9; x++) base16[2 * 16 + x] = 0xFFD9C39A;
                for (int y = 5; y <= 13; y++) for (int x = 4; x <= 11; x++) {
                    base16[y * 16 + x] = (y == 5 || y == 13 || x == 4 || x == 11) ? 0xFFE5F6FF : (y <= 7 ? 0xAAFFFFFF : mid);
                }
            }
            case "orb" -> {
                for (int y = 2; y <= 13; y++) for (int x = 2; x <= 13; x++) {
                    double d = Math.hypot(x - 7.5, y - 7.5);
                    if (d <= 5.5) base16[y * 16 + x] = d > 4.6 ? dark : d < 2.2 ? light : mid;
                }
            }
            case "solid" -> Arrays.fill(base16, mid);
            default -> { // "block"
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    boolean rim = x == 0 || y == 0 || x == 15 || y == 15;
                    base16[y * 16 + x] = rim ? dark : (rng.nextInt(5) == 0 ? light : rng.nextInt(4) == 0 ? 0xFF000000 | darken(c1, 0.85) : mid);
                }
            }
        }
        return size == 16 ? base16 : Images.resize(base16, 16, 16, size, size);
    }
}
