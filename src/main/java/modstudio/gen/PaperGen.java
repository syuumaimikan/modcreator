package modstudio.gen;

import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Paper / Spigot plugin. Items become custom-model-data items, recipes use the Bukkit API and all other data
 * (functions, procedures, loot tables, advancements, tags) ships as a data pack the plugin installs on start.
 */
public final class PaperGen {
    private final GenContext ctx;
    private final String res = "src/main/resources/";
    private final String main;

    public PaperGen(GenContext ctx) {
        this.ctx = ctx;
        this.main = ctx.mainClass + "Plugin";
    }

    private boolean paperApi() {
        return !ctx.version("api").equalsIgnoreCase("spigot");
    }

    public void generate() {
        buildFiles();
        pluginYml();
        mainClass();
        procedures();
        customItems();
        recipes();
        listener();
        adminCommand();
        String dp = res + "datapack/";
        new DataGen(ctx, dp, false, true).generate(false);
        ctx.files.json(dp + "pack.mcmeta", J.obj("pack", J.obj("pack_format", ctx.v.dataFormat, "description", ctx.p.meta.name + " (plugin data)")));
        StringBuilder index = new StringBuilder();
        for (String path : ctx.files.paths()) {
            if (path.startsWith(dp)) index.append(path.substring(dp.length())).append('\n');
        }
        ctx.files.text(dp + "files.txt", index.toString());
        for (Block b : ctx.all(Block.class)) ctx.note("block " + b.name + " is not supported by plugins");
        JavaCommon.codeFiles(ctx);
    }

    private void buildFiles() {
        boolean paper = paperApi();
        String artifact = ctx.version("api_artifact");
        String repo = paper
                ? "    maven {\n        name = 'papermc'\n        url = 'https://repo.papermc.io/repository/maven-public/'\n    }\n"
                : "    maven {\n        name = 'spigotmc'\n        url = 'https://hub.spigotmc.org/nexus/content/repositories/snapshots/'\n    }\n"
                + "    maven {\n        name = 'sonatype'\n        url = 'https://oss.sonatype.org/content/repositories/snapshots'\n    }\n";
        String dep = paper ? "io.papermc.paper:paper-api:" + artifact : "org.spigotmc:spigot-api:" + artifact;
        ctx.files.text("settings.gradle", "rootProject.name = '" + ctx.ns + "'\n");
        ctx.files.text("gradle.properties", "org.gradle.jvmargs=-Xmx1G\n");
        ctx.files.text("gradle/wrapper/gradle-wrapper.properties", Scaffold.wrapperProperties(ctx.version("gradle")));
        // run-paper downloads a Paper server for the play test ("runServer").
        String runPlugin = paper ? "    id 'xyz.jpenilla.run-paper' version '2.3.1'\n" : "";
        String runTask = paper ? "\ntasks.named('runServer') {\n    minecraftVersion('" + ctx.v.id + "')\n}\n" : "";
        ctx.files.text("build.gradle", """
                plugins {
                    id 'java'
                %s}

                group = '%s'
                version = '%s'

                repositories {
                    mavenCentral()
                %s}

                dependencies {
                    compileOnly '%s'
                }

                base {
                    archivesName = '%s'
                }

                tasks.withType(JavaCompile).configureEach {
                    options.encoding = 'UTF-8'
                    options.release = %d
                }
                """.formatted(runPlugin, ctx.pkg, ctx.p.meta.version, repo, dep, ctx.ns, ctx.v.java) + runTask);
        ctx.files.text(".gitignore", Scaffold.GITIGNORE);
    }

    private static String yaml(String s) {
        return "'" + (s == null ? "" : s.replace("'", "''").replace("\n", " ")) + "'";
    }

    private String pluginName() {
        return pluginName(ctx.p);
    }

    /** Plugin name from plugin.yml; also the name of the plugin's data folder. */
    public static String pluginName(Project p) {
        String n = p.meta.name.replaceAll("[^A-Za-z0-9_.-]", "");
        return n.length() < 2 ? p.meta.modId : n;
    }

    private void pluginYml() {
        Project.Meta m = ctx.p.meta;
        StringBuilder y = new StringBuilder();
        y.append("name: ").append(yaml(pluginName())).append('\n');
        y.append("version: ").append(yaml(m.version)).append('\n');
        y.append("main: ").append(ctx.pkg).append('.').append(main).append('\n');
        y.append("api-version: ").append(yaml(ctx.v.apiVersion)).append('\n');
        if (!m.description.isBlank()) y.append("description: ").append(yaml(m.description)).append('\n');
        if (!m.authors.isBlank()) {
            y.append("authors: [");
            List<String> a = new ArrayList<>();
            for (String s : m.authors.split(",")) if (!s.isBlank()) a.add(yaml(s.trim()));
            y.append(String.join(", ", a)).append("]\n");
        }
        if (!m.website.isBlank()) y.append("website: ").append(yaml(m.website)).append('\n');
        y.append("commands:\n");
        y.append("  ").append(ctx.ns).append(":\n");
        y.append("    description: ").append(yaml("Give and list " + m.name + " items")).append('\n');
        y.append("    usage: ").append(yaml("/" + ctx.ns + " <give|items> [item] [player] [count]")).append('\n');
        y.append("    permission: ").append(ctx.ns).append(".admin\n");
        for (Elements.Command c : ctx.all(Elements.Command.class)) {
            y.append("  ").append(c.name).append(":\n");
            y.append("    description: ").append(yaml(c.description.isBlank() ? "/" + c.name : c.description)).append('\n');
            y.append("    permission: ").append(ctx.ns).append(".command.").append(c.name).append('\n');
        }
        y.append("permissions:\n");
        y.append("  ").append(ctx.ns).append(".admin:\n    default: op\n");
        for (Elements.Command c : ctx.all(Elements.Command.class)) {
            y.append("  ").append(ctx.ns).append(".command.").append(c.name).append(":\n");
            y.append("    default: ").append(c.permission > 0 ? "op" : "true").append('\n');
        }
        ctx.files.text(res + "plugin.yml", y.toString());
    }

    private void mainClass() {
        Src s = new Src(ctx.pkg);
        s.imp("org.bukkit.Bukkit", "org.bukkit.command.PluginCommand", "org.bukkit.entity.Entity", "org.bukkit.plugin.java.JavaPlugin",
                "java.io.File", "java.io.IOException", "java.io.InputStream", "java.nio.charset.StandardCharsets",
                "java.nio.file.Files", "java.util.Arrays", "java.util.List");
        s.l("public final class " + main + " extends JavaPlugin {");
        s.l("public static final String MODID = " + Src.str(ctx.ns) + ";");
        s.l("private static " + main + " instance;");
        s.blank();
        s.l("public static " + main + " get() {").l("return instance;").l("}");
        s.blank();
        s.l("@Override");
        s.l("public void onEnable() {");
        s.l("instance = this;");
        s.l("CustomItems.init();");
        s.l("ModRecipes.register(this);");
        s.l("getServer().getPluginManager().registerEvents(new ModListener(), this);");
        s.l("PluginCommand admin = getCommand(MODID);");
        s.l("if (admin != null) {");
        s.l("AdminCommand handler = new AdminCommand();");
        s.l("admin.setExecutor(handler);");
        s.l("admin.setTabCompleter(handler);");
        s.l("}");
        BridgeGen bridge = new BridgeGen(ctx);
        for (Elements.Command c : ctx.all(Elements.Command.class)) {
            String tree = bridge.paperTree(c);
            if (tree == null) {
                s.l("command(" + Src.str(c.name) + ", " + Src.str("commands/" + c.name) + ");");
                continue;
            }
            s.l("PluginCommand tree" + Ids.toId(c.name).replace("_", "") + " = getCommand(" + Src.str(c.name) + ");");
            s.l("if (tree" + Ids.toId(c.name).replace("_", "") + " != null) {");
            s.l("CommandTree handler = " + tree + ";");
            s.l("tree" + Ids.toId(c.name).replace("_", "") + ".setExecutor(handler);");
            s.l("tree" + Ids.toId(c.name).replace("_", "") + ".setTabCompleter(handler);");
            s.l("}");
        }
        s.l("getServer().getScheduler().runTask(this, this::installDatapack);");
        if (bridge.paperNeeded()) s.l("Bridge.start(this);");
        s.l("}");
        s.blank();
        if (bridge.paperNeeded()) {
            s.l("@Override");
            s.l("public void onDisable() {");
            s.l("Bridge.stop();");
            s.l("}");
            s.blank();
        }
        bridge.paper(main);
        s.l("private void command(String name, String function) {");
        s.l("PluginCommand command = getCommand(name);");
        s.l("if (command == null) return;");
        s.l("command.setExecutor((sender, cmd, label, args) -> {");
        s.l("if (sender instanceof Entity entity) Procedures.run(entity, function);");
        s.l("else Procedures.runServer(function);");
        s.l("return true;");
        s.l("});");
        s.l("}");
        s.blank();
        s.l("/** Copies the bundled data pack into the main world and reloads data when it changed. */");
        s.l("private void installDatapack() {");
        s.l("try (InputStream index = getResource(\"datapack/files.txt\")) {");
        s.l("if (index == null) return;");
        s.l("List<String> files = new String(index.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();");
        s.l("File root = new File(Bukkit.getWorlds().get(0).getWorldFolder(), \"datapacks/\" + MODID + \"_plugin\");");
        s.l("boolean changed = false;");
        s.l("for (String path : files) {");
        s.l("try (InputStream in = getResource(\"datapack/\" + path)) {");
        s.l("if (in == null) continue;");
        s.l("byte[] data = in.readAllBytes();");
        s.l("File out = new File(root, path);");
        s.l("if (out.exists() && Arrays.equals(Files.readAllBytes(out.toPath()), data)) continue;");
        s.l("out.getParentFile().mkdirs();");
        s.l("Files.write(out.toPath(), data);");
        s.l("changed = true;");
        s.l("}");
        s.l("}");
        s.l("if (changed) {");
        s.l("getLogger().info(\"Installed data pack, reloading data\");");
        boolean worldgen = !ctx.all(modstudio.core.Systems.Dimension.class).isEmpty() || !ctx.all(modstudio.core.Systems.Biome.class).isEmpty()
                || ctx.all(modstudio.core.Systems.Structure.class).stream().anyMatch(x -> x.generate);
        if (worldgen) s.l("getLogger().warning(\"New dimensions, biomes and structures load after the next server restart\");");
        if (ctx.v.dialogs() && !ctx.all(modstudio.core.Systems.Dialog.class).isEmpty())
            s.l("getLogger().warning(\"New dialogs load after the next server restart\");");
        s.l("Bukkit.reloadData();");
        s.l("}");
        s.l("} catch (IOException e) {");
        s.l("getLogger().warning(\"Could not install data pack: \" + e.getMessage());");
        s.l("}");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + main + ".java", s.toString());
    }

    private void procedures() {
        Src s = new Src(ctx.pkg);
        s.imp("org.bukkit.Bukkit", "org.bukkit.entity.Entity");
        s.l("/** Runs procedures compiled to data pack functions. */");
        s.l("public final class Procedures {");
        s.l("private Procedures() {").l("}");
        s.blank();
        s.l("public static void run(Entity entity, String function) {");
        s.l("Bukkit.dispatchCommand(Bukkit.getConsoleSender(), \"execute as \" + entity.getUniqueId() + \" at @s run function \" + " + main + ".MODID + \":\" + function);");
        s.l("}");
        s.blank();
        s.l("public static void runServer(String function) {");
        s.l("Bukkit.dispatchCommand(Bukkit.getConsoleSender(), \"function \" + " + main + ".MODID + \":\" + function);");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "Procedures.java", s.toString());
    }

    private void customItems() {
        boolean modern = ctx.components();
        Src s = new Src(ctx.pkg);
        s.imp("org.bukkit.Material", "org.bukkit.NamespacedKey", "org.bukkit.inventory.ItemStack", "org.bukkit.inventory.meta.ItemMeta",
                "org.bukkit.persistence.PersistentDataType", "java.util.ArrayList", "java.util.LinkedHashMap", "java.util.List",
                "java.util.Map", "java.util.Set", "java.util.function.Supplier");
        if (modern) s.imp("org.bukkit.inventory.ItemRarity");
        else s.imp("org.bukkit.ChatColor", "org.bukkit.enchantments.Enchantment", "org.bukkit.inventory.ItemFlag");
        s.l("/** Custom items represented by a vanilla base item with custom model data. */");
        s.l("public final class CustomItems {");
        s.l("public static final NamespacedKey KEY = NamespacedKey.fromString(" + Src.str(ctx.ns + ":item_id") + ");");
        s.l("private static final String MODID_PREFIX = " + Src.str(ctx.ns + ":") + ";");
        s.l("private static final Map<String, Supplier<ItemStack>> ITEMS = new LinkedHashMap<>();");
        s.blank();
        s.l("private CustomItems() {").l("}");
        s.blank();
        s.l("public static void init() {");
        for (Item it : ctx.all(Item.class)) {
            List<String> lore = new ArrayList<>();
            String tip = it.shiftTooltip.isBlank() ? it.tooltip : (it.tooltip.isBlank() ? "" : it.tooltip + "\n") + it.shiftTooltip;
            if (!tip.isBlank()) for (String l : tip.split("\\R")) lore.add(Src.str((l.contains("&") ? "" : "§7") + GenContext.legacy(l)));
            String name = it.displayName.isBlank() ? Ids.titleCase(it.name) : it.displayName;
            int stack = it.damageable() ? 1 : Math.max(1, Math.min(99, it.maxStack));
            if (it.isArmor() && ctx.p.byName(ElementType.ARMOR_MATERIAL, it.armorMaterial) != null) {
                // armor attributes, durability and the worn look are item components: parse the same stack as /give
                s.imp("org.bukkit.Bukkit");
                s.l("ITEMS.put(" + Src.str(it.name) + ", () -> Bukkit.getItemFactory().createItemStack(" + Src.str(ItemSpec.give(ctx, it)) + "));");
                continue;
            }
            s.l("ITEMS.put(" + Src.str(it.name) + ", () -> build(" + Src.str(it.name) + ", " + Src.str(ItemSpec.base(it)) + ", "
                    + Src.str(name) + ", " + Src.str(it.rarity) + ", List.of(" + String.join(", ", lore) + "), "
                    + ItemSpec.modelData(it) + ", " + it.glint + ", " + stack + ", " + it.fireResistant + "));");
        }
        s.l("}");
        s.blank();
        s.l("public static Set<String> ids() {").l("return ITEMS.keySet();").l("}");
        s.blank();
        s.l("public static ItemStack create(String id) {");
        s.l("Supplier<ItemStack> supplier = ITEMS.get(id);");
        s.l("return supplier == null ? null : supplier.get();");
        s.l("}");
        s.blank();
        s.l("public static String idOf(ItemStack stack) {");
        s.l("if (stack == null || !stack.hasItemMeta()) return null;");
        s.l("return stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);");
        s.l("}");
        s.blank();
        s.l("private static ItemStack build(String id, String base, String name, String rarity, List<String> lore, int modelData, boolean glint, int maxStack, boolean fireResistant) {");
        s.l("Material material = Material.matchMaterial(base);");
        s.l("ItemStack stack = new ItemStack(material == null ? Material.PAPER : material);");
        s.l("ItemMeta meta = stack.getItemMeta();");
        s.l("if (meta == null) return stack;");
        if (modern) {
            s.l("meta.setItemName(name);");
            s.l("if (!rarity.equals(\"common\")) meta.setRarity(ItemRarity.valueOf(rarity.toUpperCase()));");
            s.l("if (glint) meta.setEnchantmentGlintOverride(true);");
            s.l("if (maxStack != stack.getMaxStackSize()) meta.setMaxStackSize(maxStack);");
            s.l("if (fireResistant) meta.setFireResistant(true);");
        } else {
            s.l("ChatColor color = switch (rarity) {");
            s.l("case \"uncommon\" -> ChatColor.YELLOW;");
            s.l("case \"rare\" -> ChatColor.AQUA;");
            s.l("case \"epic\" -> ChatColor.LIGHT_PURPLE;");
            s.l("default -> ChatColor.WHITE;");
            s.l("};");
            s.l("meta.setDisplayName(ChatColor.RESET.toString() + color + name);");
            s.l("if (glint) {");
            s.l("meta.addEnchant(Enchantment.LUCK, 1, true);");
            s.l("meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);");
            s.l("}");
        }
        s.l("if (!lore.isEmpty()) meta.setLore(new ArrayList<>(lore));");
        if (ctx.v.itemDefinitions()) s.l("meta.setItemModel(NamespacedKey.fromString(MODID_PREFIX + id));");
        else s.l("meta.setCustomModelData(modelData);");
        s.l("meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);");
        s.l("stack.setItemMeta(meta);");
        s.l("return stack;");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "CustomItems.java", s.toString());
    }

    private void recipes() {
        Src s = new Src(ctx.pkg);
        s.imp("org.bukkit.Bukkit", "org.bukkit.Material", "org.bukkit.NamespacedKey", "org.bukkit.Tag",
                "org.bukkit.inventory.ItemStack", "org.bukkit.inventory.Recipe", "org.bukkit.inventory.RecipeChoice",
                "org.bukkit.plugin.java.JavaPlugin");
        s.l("public final class ModRecipes {");
        s.l("private ModRecipes() {").l("}");
        s.blank();
        s.l("public static void register(JavaPlugin plugin) {");
        int n = 0;
        for (Recipe r : ctx.all(Recipe.class)) {
            if (r.result.isBlank()) continue;
            String key = "new NamespacedKey(plugin, " + Src.str(r.name) + ")";
            String var = "r" + (n++);
            String result = "stack(" + Src.str(Ids.full(r.result)) + ", " + Math.max(1, r.count) + ")";
            switch (r.recipeType) {
                case "shaped" -> {
                    DataGen.Shape sh = DataGen.Shape.of(r.grid);
                    if (sh.rows.isEmpty()) continue;
                    s.imp("org.bukkit.inventory.ShapedRecipe");
                    s.l("try {");
                    s.l("ShapedRecipe " + var + " = new ShapedRecipe(" + key + ", " + result + ");");
                    List<String> rows = new ArrayList<>();
                    for (String row : sh.rows) rows.add(Src.str(row));
                    s.l(var + ".shape(" + String.join(", ", rows) + ");");
                    for (var e : sh.keys.entrySet()) s.l(var + ".setIngredient('" + e.getValue() + "', choice(" + Src.str(e.getKey()) + "));");
                    s.l("add(" + var + ");");
                    s.l("} catch (RuntimeException e) {");
                    s.l("plugin.getLogger().warning(\"Recipe " + r.name + " skipped: \" + e.getMessage());");
                    s.l("}");
                }
                case "shapeless" -> {
                    s.imp("org.bukkit.inventory.ShapelessRecipe");
                    s.l("try {");
                    s.l("ShapelessRecipe " + var + " = new ShapelessRecipe(" + key + ", " + result + ");");
                    for (String g : r.grid) if (!g.isBlank()) s.l(var + ".addIngredient(choice(" + Src.str(Ids.full(g)) + "));");
                    s.l("add(" + var + ");");
                    s.l("} catch (RuntimeException e) {");
                    s.l("plugin.getLogger().warning(\"Recipe " + r.name + " skipped: \" + e.getMessage());");
                    s.l("}");
                }
                case "smelting", "blasting", "smoking", "campfire" -> {
                    String cls = switch (r.recipeType) {
                        case "blasting" -> "BlastingRecipe";
                        case "smoking" -> "SmokingRecipe";
                        case "campfire" -> "CampfireRecipe";
                        default -> "FurnaceRecipe";
                    };
                    s.imp("org.bukkit.inventory." + cls);
                    s.l("try {");
                    s.l("add(new " + cls + "(" + key + ", stack(" + Src.str(Ids.full(r.result)) + ", 1), choice(" + Src.str(Ids.full(r.input))
                            + "), " + Src.f(r.experience) + ", " + r.cookTime + "));");
                    s.l("} catch (RuntimeException e) {");
                    s.l("plugin.getLogger().warning(\"Recipe " + r.name + " skipped: \" + e.getMessage());");
                    s.l("}");
                }
                case "stonecutting" -> {
                    s.imp("org.bukkit.inventory.StonecuttingRecipe");
                    s.l("try {");
                    s.l("add(new StonecuttingRecipe(" + key + ", " + result + ", choice(" + Src.str(Ids.full(r.input)) + ")));");
                    s.l("} catch (RuntimeException e) {");
                    s.l("plugin.getLogger().warning(\"Recipe " + r.name + " skipped: \" + e.getMessage());");
                    s.l("}");
                }
                case "smithing" -> {
                    s.imp("org.bukkit.inventory.SmithingTransformRecipe");
                    s.l("try {");
                    s.l("add(new SmithingTransformRecipe(" + key + ", stack(" + Src.str(Ids.full(r.result)) + ", 1), choice(" + Src.str(Ids.full(r.template))
                            + "), choice(" + Src.str(Ids.full(r.base)) + "), choice(" + Src.str(Ids.full(r.addition)) + ")));");
                    s.l("} catch (RuntimeException e) {");
                    s.l("plugin.getLogger().warning(\"Recipe " + r.name + " skipped: \" + e.getMessage());");
                    s.l("}");
                }
                case "brewing" -> {
                    String in = Ids.full(r.input.trim()), out = Ids.full(r.result.trim());
                    if (!in.startsWith("minecraft:") || !out.startsWith("minecraft:") || r.addition.isBlank()) continue;
                    s.imp("io.papermc.paper.potion.PotionMix");
                    s.l("try {");
                    s.l("for (String bottle : new String[] {\"potion\", \"splash_potion\", \"lingering_potion\"}) {");
                    s.l("Bukkit.getPotionBrewer().addPotionMix(new PotionMix(new NamespacedKey(plugin, " + Src.str(r.name + "_") + " + bottle), potion(bottle, "
                            + Src.str(out) + "), new RecipeChoice.ExactChoice(potion(bottle, " + Src.str(in) + ")), choice(" + Src.str(Ids.full(r.addition.trim())) + ")));");
                    s.l("}");
                    s.l("} catch (RuntimeException e) {");
                    s.l("plugin.getLogger().warning(\"Recipe " + r.name + " skipped: \" + e.getMessage());");
                    s.l("}");
                }
                default -> {
                }
            }
        }
        s.l("}");
        s.blank();
        s.l("/** A drinkable, splash or lingering bottle of a potion. */");
        s.l("static ItemStack potion(String bottle, String potion) {");
        if (ctx.components()) s.l("return Bukkit.getItemFactory().createItemStack(\"minecraft:\" + bottle + \"[potion_contents={potion:\\\"\" + potion + \"\\\"}]\");");
        else s.l("return Bukkit.getItemFactory().createItemStack(\"minecraft:\" + bottle + \"{Potion:\\\"\" + potion + \"\\\"}\");");
        s.l("}");
        s.blank();
        s.l("private static void add(Recipe recipe) {");
        s.l("Bukkit.addRecipe(recipe);");
        s.l("}");
        s.blank();
        s.l("static RecipeChoice choice(String id) {");
        s.l("if (id.startsWith(\"#\")) {");
        s.l("Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.fromString(id.substring(1)), Material.class);");
        s.l("if (tag == null) throw new IllegalArgumentException(\"Unknown tag \" + id);");
        s.l("return new RecipeChoice.MaterialChoice(tag);");
        s.l("}");
        s.l("if (id.startsWith(" + main + ".MODID + \":\")) {");
        s.l("ItemStack custom = CustomItems.create(id.substring(" + main + ".MODID.length() + 1));");
        s.l("if (custom == null) throw new IllegalArgumentException(\"Unknown item \" + id);");
        s.l("return new RecipeChoice.ExactChoice(custom);");
        s.l("}");
        s.l("Material material = Material.matchMaterial(id);");
        s.l("if (material == null) throw new IllegalArgumentException(\"Unknown item \" + id);");
        s.l("return new RecipeChoice.MaterialChoice(material);");
        s.l("}");
        s.blank();
        s.l("static ItemStack stack(String id, int count) {");
        s.l("if (id.startsWith(" + main + ".MODID + \":\")) {");
        s.l("ItemStack custom = CustomItems.create(id.substring(" + main + ".MODID.length() + 1));");
        s.l("if (custom == null) throw new IllegalArgumentException(\"Unknown item \" + id);");
        s.l("custom.setAmount(count);");
        s.l("return custom;");
        s.l("}");
        s.l("Material material = Material.matchMaterial(id);");
        s.l("if (material == null) throw new IllegalArgumentException(\"Unknown item \" + id);");
        s.l("return new ItemStack(material, count);");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModRecipes.java", s.toString());
    }

    private void listener() {
        Src s = new Src(ctx.pkg);
        s.imp("org.bukkit.event.EventHandler", "org.bukkit.event.Listener");
        s.l("public final class ModListener implements Listener {");
        List<Procedure> rc = new ArrayList<>();
        for (Procedure p : ctx.all(Procedure.class)) if (p.trigger.equals("item_right_click") && !p.triggerValue.isBlank()) rc.add(p);
        if (!rc.isEmpty()) {
            s.imp("org.bukkit.Bukkit", "org.bukkit.Material", "org.bukkit.NamespacedKey", "org.bukkit.Tag",
                    "org.bukkit.event.block.Action", "org.bukkit.event.player.PlayerInteractEvent",
                    "org.bukkit.inventory.EquipmentSlot", "org.bukkit.inventory.ItemStack");
            s.l("@EventHandler");
            s.l("public void onInteract(PlayerInteractEvent event) {");
            s.l("if (event.getHand() != EquipmentSlot.HAND) return;");
            s.l("if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;");
            s.l("ItemStack stack = event.getItem();");
            s.l("if (stack == null) return;");
            s.l("String custom = CustomItems.idOf(stack);");
            s.l("String vanilla = stack.getType().getKey().toString();");
            for (Procedure p : rc) {
                String id = Ids.full(p.triggerValue);
                String cond;
                if (id.startsWith(ctx.ns + ":")) cond = Src.str(Ids.path(id)) + ".equals(custom)";
                else if (Ids.isTag(id)) cond = "custom == null && isTagged(" + Src.str(id.substring(1)) + ", stack.getType())";
                else cond = "custom == null && " + Src.str(id) + ".equals(vanilla)";
                s.l("if (" + cond + ") Procedures.run(event.getPlayer(), " + Src.str("procedures/" + p.name) + ");");
            }
            s.l("}");
            s.blank();
            s.l("private static boolean isTagged(String tag, Material material) {");
            s.l("Tag<Material> t = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.fromString(tag), Material.class);");
            s.l("return t != null && t.isTagged(material);");
            s.l("}");
        }
        lootInjection(s);
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModListener.java", s.toString());
    }

    /**
     * Rolls the project's tables together with existing ones: chests through the loot event, mobs on death, and
     * blocks with /loot at the block, since block tables need the tool and state that plugins cannot pass.
     */
    private void lootInjection(Src s) {
        List<Elements.LootTable> injects = HooksGen.injects(ctx);
        if (injects.isEmpty()) return;
        s.imp("org.bukkit.Bukkit", "org.bukkit.Location", "org.bukkit.NamespacedKey", "org.bukkit.loot.LootContext", "org.bukkit.loot.LootTable",
                "org.bukkit.loot.Lootable", "org.bukkit.event.world.LootGenerateEvent", "org.bukkit.event.entity.EntityDeathEvent",
                "org.bukkit.event.block.BlockDropItemEvent", "java.util.ArrayList", "java.util.HashMap", "java.util.List", "java.util.Map",
                "java.util.Random");
        s.blank();
        s.l("private static final Random RANDOM = new Random();");
        s.l("private static final Map<String, List<String>> INJECTS = new HashMap<>();");
        s.blank();
        s.l("static {");
        for (Elements.LootTable lt : injects) {
            s.l("INJECTS.computeIfAbsent(" + Src.str(Ids.full(lt.replaces.trim())) + ", k -> new ArrayList<>()).add(" + Src.str(HooksGen.injectId(ctx.ns, lt)) + ");");
        }
        s.l("}");
        s.blank();
        s.l("private static List<LootTable> injected(String target) {");
        s.l("List<LootTable> r = new ArrayList<>();");
        s.l("for (String id : INJECTS.getOrDefault(target, List.of())) {");
        s.l("LootTable table = Bukkit.getLootTable(NamespacedKey.fromString(id));");
        s.l("if (table != null) r.add(table);");
        s.l("}");
        s.l("return r;");
        s.l("}");
        s.blank();
        s.l("@EventHandler");
        s.l("public void onLootGenerate(LootGenerateEvent event) {");
        s.l("if (event.getLootTable() == null) return;");
        s.l("for (LootTable table : injected(event.getLootTable().getKey().toString())) event.getLoot().addAll(table.populateLoot(RANDOM, event.getLootContext()));");
        s.l("}");
        s.blank();
        s.l("@EventHandler");
        s.l("public void onDeath(EntityDeathEvent event) {");
        s.l("if (!(event.getEntity() instanceof Lootable lootable) || lootable.getLootTable() == null) return;");
        s.l("for (LootTable table : injected(lootable.getLootTable().getKey().toString())) {");
        s.l("LootContext.Builder context = new LootContext.Builder(event.getEntity().getLocation()).lootedEntity(event.getEntity());");
        s.l("if (event.getEntity().getKiller() != null) context.killer(event.getEntity().getKiller());");
        s.l("event.getDrops().addAll(table.populateLoot(RANDOM, context.build()));");
        s.l("}");
        s.l("}");
        s.blank();
        s.l("@EventHandler");
        s.l("public void onBlockDrop(BlockDropItemEvent event) {");
        s.l("NamespacedKey type = event.getBlockState().getType().getKey();");
        s.l("List<String> ids = INJECTS.getOrDefault(type.getNamespace() + \":blocks/\" + type.getKey(), List.of());");
        s.l("if (ids.isEmpty()) return;");
        s.l("Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);");
        s.l("for (String id : ids) {");
        s.l("Bukkit.dispatchCommand(Bukkit.getConsoleSender(), \"execute in \" + at.getWorld().getKey() + \" run loot spawn \" + at.getX() + \" \" + at.getY() + \" \" + at.getZ() + \" loot \" + id);");
        s.l("}");
        s.l("}");
    }

    private void adminCommand() {
        Src s = new Src(ctx.pkg);
        s.imp("org.bukkit.Bukkit", "org.bukkit.command.Command", "org.bukkit.command.CommandExecutor", "org.bukkit.command.CommandSender",
                "org.bukkit.command.TabCompleter", "org.bukkit.entity.Player", "org.bukkit.inventory.ItemStack",
                "java.util.ArrayList", "java.util.List");
        s.l("/** /" + ctx.ns + " give <item> [player] [count] and /" + ctx.ns + " items */");
        s.l("public final class AdminCommand implements CommandExecutor, TabCompleter {");
        s.l("@Override");
        s.l("public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {");
        s.l("if (args.length == 0 || args[0].equalsIgnoreCase(\"items\")) {");
        s.l("sender.sendMessage(\"Items: \" + String.join(\", \", CustomItems.ids()));");
        s.l("return true;");
        s.l("}");
        s.l("if (args[0].equalsIgnoreCase(\"give\") && args.length >= 2) {");
        s.l("ItemStack stack = CustomItems.create(args[1]);");
        s.l("if (stack == null) {");
        s.l("sender.sendMessage(\"Unknown item: \" + args[1]);");
        s.l("return true;");
        s.l("}");
        s.l("Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);");
        s.l("if (target == null) {");
        s.l("sender.sendMessage(\"Player not found\");");
        s.l("return true;");
        s.l("}");
        s.l("int count = 1;");
        s.l("if (args.length >= 4) {");
        s.l("try {");
        s.l("count = Math.max(1, Integer.parseInt(args[3]));");
        s.l("} catch (NumberFormatException ignored) {");
        s.l("}");
        s.l("}");
        s.l("stack.setAmount(Math.min(count, stack.getMaxStackSize()));");
        s.l("target.getInventory().addItem(stack);");
        s.l("return true;");
        s.l("}");
        s.l("return false;");
        s.l("}");
        s.blank();
        s.l("@Override");
        s.l("public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {");
        s.l("List<String> out = new ArrayList<>();");
        s.l("if (args.length == 1) {");
        s.l("out.add(\"give\");");
        s.l("out.add(\"items\");");
        s.l("} else if (args.length == 2 && args[0].equalsIgnoreCase(\"give\")) {");
        s.l("out.addAll(CustomItems.ids());");
        s.l("} else if (args.length == 3) {");
        s.l("for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());");
        s.l("}");
        s.l("return out;");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "AdminCommand.java", s.toString());
    }
}
