package modstudio.gen;

import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.List;

/** Code shared by Forge, NeoForge and Fabric (all use official Mojang names). */
public final class JavaCommon {
    public enum Loader { FORGE, NEOFORGE, FABRIC }

    private final GenContext ctx;
    private final Loader loader;

    public JavaCommon(GenContext ctx, Loader loader) {
        this.ctx = ctx;
        this.loader = loader;
    }

    public String itemRef(String name) {
        return "ModItems." + Ids.toConst(name) + (loader == Loader.FABRIC ? "" : ".get()");
    }

    public String blockRef(String name) {
        return "ModBlocks." + Ids.toConst(name) + (loader == Loader.FABRIC ? "" : ".get()");
    }

    /** Reference to any item id: project items directly, others through the registry. */
    public String anyItemRef(String id) {
        String f = Ids.full(id);
        Element e = ctx.p.itemLike(f);
        if (e != null) return itemRef(e.name);
        return "ModProcedures.item(" + Src.str(f) + ")";
    }

    public List<Procedure> rightClickProcs(String itemId) {
        List<Procedure> r = new ArrayList<>();
        for (Procedure p : ctx.all(Procedure.class)) {
            if (p.trigger.equals("item_right_click") && Ids.full(p.triggerValue).equals(itemId)) r.add(p);
        }
        return r;
    }

    /** Right-click procedures whose item is not a project item with its own class. */
    public List<Procedure> eventRightClickProcs() {
        List<Procedure> r = new ArrayList<>();
        for (Procedure p : ctx.all(Procedure.class)) {
            if (!p.trigger.equals("item_right_click") || p.triggerValue.isBlank()) continue;
            if (ctx.p.itemLike(Ids.full(p.triggerValue)) instanceof Item) continue;
            r.add(p);
        }
        return r;
    }

    public boolean needsClass(Item it) {
        return it.glint || !it.tooltip.isBlank() || !it.shiftTooltip.isBlank() || !rightClickProcs(ctx.id(it.name)).isEmpty()
                || (loader == Loader.FORGE && it.burnTime > 0);
    }

    /** 1.21.5+: swords and pickaxes are plain items configured through Item.Properties. */
    public boolean toolProps() {
        return ctx.v.atLeast(McVersion.V1_21_8);
    }

    /** Whether the tool kind still has its own item class in this version. */
    private boolean toolClass(Item it) {
        if (!toolProps()) return true;
        return it.kind.equals("axe") || it.kind.equals("shovel") || it.kind.equals("hoe");
    }

    public String tier(String t) {
        return (ctx.registryKeys() ? "ToolMaterial." : "Tiers.") + switch (t) {
            case "wood" -> "WOOD";
            case "stone" -> "STONE";
            case "gold" -> "GOLD";
            case "diamond" -> "DIAMOND";
            case "netherite" -> "NETHERITE";
            default -> "IRON";
        };
    }

    public String superClass(Item it) {
        if (it.isTool() && !toolClass(it)) return "Item";
        return switch (it.kind) {
            case "sword" -> "SwordItem";
            case "pickaxe" -> "PickaxeItem";
            case "axe" -> "AxeItem";
            case "shovel" -> "ShovelItem";
            case "hoe" -> "HoeItem";
            default -> "Item";
        };
    }

    public String className(Item it) {
        return Ids.toPascal(it.name) + "Item";
    }

    public void importsForItems(Src s) {
        s.imp("net.minecraft.world.item.Item", "net.minecraft.world.item.Rarity", "net.minecraft.world.item.BlockItem");
        for (Item it : ctx.all(Item.class)) {
            if (it.kind.equals("food")) s.imp("net.minecraft.world.food.FoodProperties");
            if (it.isTool()) {
                s.imp(ctx.registryKeys() ? "net.minecraft.world.item.ToolMaterial" : "net.minecraft.world.item.Tiers");
                s.imp("net.minecraft.world.item." + superClass(it));
                if (ctx.components() && !ctx.registryKeys()) {
                    s.imp(it.kind.equals("sword") ? "net.minecraft.world.item.SwordItem" : "net.minecraft.world.item.DiggerItem");
                }
            }
            if (it.kind.equals("food") && it.fastFood && ctx.registryKeys()) s.imp("net.minecraft.world.item.component.Consumables");
            if (needsClass(it)) s.imp(ctx.pkg + ".item." + className(it));
        }
    }

    private String food(Item it) {
        StringBuilder sb = new StringBuilder("new FoodProperties.Builder().nutrition(").append(it.nutrition).append(')');
        sb.append(ctx.components() ? ".saturationModifier(" : ".saturationMod(").append(Src.f(it.saturation)).append(')');
        if (it.alwaysEdible) sb.append(ctx.components() ? ".alwaysEdible()" : ".alwaysEat()");
        if (it.fastFood && !ctx.registryKeys()) sb.append(".fast()");
        return sb.append(".build()").toString();
    }

    /** Main class expression for an item registry key (1.21.2+). */
    public String itemKey(String name) {
        return ctx.mainClass + ".itemKey(" + Src.str(name) + ")";
    }

    public String blockKey(String name) {
        return ctx.mainClass + ".blockKey(" + Src.str(name) + ")";
    }

    /** Properties for a block's item. */
    public String blockItemProps(String name) {
        if (!ctx.registryKeys()) return "new Item.Properties()";
        return "new Item.Properties().setId(" + itemKey(name) + ").useBlockDescriptionPrefix()";
    }

    public String itemProps(Item it) {
        StringBuilder sb = new StringBuilder("new Item.Properties()");
        if (ctx.registryKeys()) sb.append(".setId(").append(itemKey(it.name)).append(')');
        if (!it.isTool() && it.maxStack != 64) sb.append(".stacksTo(").append(Math.max(1, Math.min(64, it.maxStack))).append(')');
        if (!it.rarity.equals("common")) sb.append(".rarity(Rarity.").append(it.rarity.toUpperCase()).append(')');
        if (it.fireResistant) sb.append(".fireResistant()");
        if (it.kind.equals("food")) {
            sb.append(".food(").append(food(it));
            if (it.fastFood && ctx.registryKeys()) sb.append(", Consumables.defaultFood().consumeSeconds(0.8F).build()");
            sb.append(')');
        }
        if (it.isTool() && !toolClass(it)) {
            sb.append('.').append(it.kind).append('(').append(tier(it.tier)).append(", ").append(Src.f(it.attackDamage))
                    .append(", ").append(Src.f(it.attackSpeed)).append(')');
        }
        return sb.toString();
    }

    public String itemConstruct(Item it) {
        String cls = needsClass(it) ? className(it) : superClass(it);
        String props = itemProps(it);
        if (!it.isTool() || !toolClass(it)) return "new " + cls + "(" + props + ")";
        String tier = tier(it.tier);
        boolean intDamage = it.kind.equals("sword") || it.kind.equals("pickaxe") || it.kind.equals("hoe");
        if (ctx.registryKeys()) {
            return "new " + cls + "(" + tier + ", " + Src.f(it.attackDamage) + ", " + Src.f(it.attackSpeed) + ", " + props + ")";
        }
        if (ctx.components()) {
            String attrs = it.kind.equals("sword")
                    ? "SwordItem.createAttributes(" + tier + ", " + (int) Math.round(it.attackDamage) + ", " + Src.f(it.attackSpeed) + ")"
                    : "DiggerItem.createAttributes(" + tier + ", " + Src.f(it.attackDamage) + ", " + Src.f(it.attackSpeed) + ")";
            return "new " + cls + "(" + tier + ", " + props + ".attributes(" + attrs + "))";
        }
        String dmg = intDamage ? String.valueOf((int) Math.round(it.attackDamage)) : Src.f(it.attackDamage);
        return "new " + cls + "(" + tier + ", " + dmg + ", " + Src.f(it.attackSpeed) + ", " + props + ")";
    }

    /** Custom item subclass for glint, tooltips, right-click procedures and fuel. */
    public void itemClass(Item it) {
        String cls = className(it);
        String sup = superClass(it);
        Src s = new Src(ctx.pkg + ".item");
        s.imp("net.minecraft.world.item.Item", "net.minecraft.world.item.ItemStack");
        if (!sup.equals("Item")) {
            s.imp("net.minecraft.world.item." + sup, ctx.registryKeys() ? "net.minecraft.world.item.ToolMaterial" : "net.minecraft.world.item.Tier");
        }
        s.l("public class " + cls + " extends " + sup + " {");
        boolean intDamage = it.kind.equals("sword") || it.kind.equals("pickaxe") || it.kind.equals("hoe");
        if (!it.isTool() || !toolClass(it)) {
            s.l("public " + cls + "(Item.Properties properties) {").l("super(properties);").l("}");
        } else if (ctx.registryKeys()) {
            s.l("public " + cls + "(ToolMaterial material, float attackDamage, float attackSpeed, Item.Properties properties) {")
                    .l("super(material, attackDamage, attackSpeed, properties);").l("}");
        } else if (ctx.components()) {
            s.l("public " + cls + "(Tier tier, Item.Properties properties) {").l("super(tier, properties);").l("}");
        } else {
            String dt = intDamage ? "int" : "float";
            s.l("public " + cls + "(Tier tier, " + dt + " attackDamage, float attackSpeed, Item.Properties properties) {")
                    .l("super(tier, attackDamage, attackSpeed, properties);").l("}");
        }
        if (it.glint) {
            s.blank().l("@Override").l("public boolean isFoil(ItemStack stack) {").l("return true;").l("}");
        }
        if (!it.tooltip.isBlank() || !it.shiftTooltip.isBlank()) {
            s.imp("java.util.List", "net.minecraft.network.chat.Component", "net.minecraft.world.item.TooltipFlag");
            s.blank().l("@Override");
            String add = "tooltip.add";
            if (toolProps()) {
                s.imp("java.util.function.Consumer", "net.minecraft.world.item.component.TooltipDisplay");
                s.l("public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {");
                add = "tooltip.accept";
            } else if (ctx.components()) {
                s.l("public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {");
            } else {
                s.imp("net.minecraft.world.level.Level");
                s.l("public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {");
            }
            if (!it.tooltip.isBlank()) for (String line : it.tooltip.split("\\R")) {
                String txt = line.contains("&") ? GenContext.legacy(line) : "§7" + line;
                s.l(add + "(Component.literal(" + Src.str(txt) + "));");
            }
            if (!it.shiftTooltip.isBlank()) {
                // client only: appendHoverText never runs on a dedicated server
                s.imp("net.minecraft.client.gui.screens.Screen", "net.minecraft.ChatFormatting");
                s.l("if (Screen.hasShiftDown()) {");
                for (String line : it.shiftTooltip.split("\\R")) {
                    String txt = line.contains("&") ? GenContext.legacy(line) : "§7" + line;
                    s.l(add + "(Component.literal(" + Src.str(txt) + "));");
                }
                s.l("} else {");
                s.l(add + "(Component.translatable(" + Src.str(ctx.ns + ".tooltip.hold_shift") + ").withStyle(ChatFormatting.DARK_GRAY));");
                s.l("}");
            }
            if (toolProps()) s.l("super.appendHoverText(stack, context, display, tooltip, flag);");
            else s.l(ctx.components() ? "super.appendHoverText(stack, context, tooltip, flag);" : "super.appendHoverText(stack, level, tooltip, flag);");
            s.l("}");
        }
        List<Procedure> procs = rightClickProcs(ctx.id(it.name));
        if (!procs.isEmpty()) {
            s.imp("net.minecraft.world.InteractionHand", "net.minecraft.world.entity.player.Player", "net.minecraft.world.level.Level",
                    ctx.pkg + ".ModProcedures");
            if (ctx.registryKeys()) {
                s.imp("net.minecraft.world.InteractionResult");
                s.blank().l("@Override").l("public InteractionResult use(Level level, Player player, InteractionHand hand) {");
            } else {
                s.imp("net.minecraft.world.InteractionResultHolder");
                s.blank().l("@Override").l("public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {");
            }
            s.l("if (!level.isClientSide()) {");
            for (Procedure p : procs) s.l("ModProcedures.run(player, " + Src.str("procedures/" + p.name) + ");");
            s.l("}");
            if (!it.kind.equals("basic")) s.l("return super.use(level, player, hand);");
            else if (ctx.registryKeys()) s.l("return InteractionResult.SUCCESS;");
            else s.l("return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());");
            s.l("}");
        }
        if (loader == Loader.FORGE && it.burnTime > 0) {
            s.imp("net.minecraft.world.item.crafting.RecipeType");
            s.blank().l("@Override").l("public int getBurnTime(ItemStack itemStack, RecipeType<?> recipeType) {")
                    .l("return " + it.burnTime + ";").l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "item/" + cls + ".java", s.toString());
    }

    public static String soundType(String s) {
        return "SoundType." + switch (s) {
            case "netherite" -> "NETHERITE_BLOCK";
            case "bone" -> "BONE_BLOCK";
            case "slime" -> "SLIME_BLOCK";
            default -> s.toUpperCase();
        };
    }

    public String blockProps(Block b) {
        StringBuilder sb = new StringBuilder("BlockBehaviour.Properties.of()");
        if (ctx.registryKeys()) sb.append(".setId(").append(blockKey(b.name)).append(')');
        sb.append(".strength(").append(Src.f(b.hardness)).append(", ").append(Src.f(b.resistance)).append(')');
        sb.append(".sound(").append(soundType(b.sound)).append(')');
        if (b.light > 0) sb.append(".lightLevel(state -> ").append(Math.min(15, b.light)).append(')');
        if (b.requiresTool && !b.tool.equals("none")) sb.append(".requiresCorrectToolForDrops()");
        if (b.cutout()) sb.append(".noOcclusion()");
        if (b.shape.equals("cross")) sb.append(".noCollission()");
        if (Math.abs(b.friction - 0.6) > 1e-6) sb.append(".friction(").append(Src.f(b.friction)).append(')');
        if (Math.abs(b.speedFactor - 1) > 1e-6) sb.append(".speedFactor(").append(Src.f(b.speedFactor)).append(')');
        if (Math.abs(b.jumpFactor - 1) > 1e-6) sb.append(".jumpFactor(").append(Src.f(b.jumpFactor)).append(')');
        return sb.toString();
    }

    public String blockConstruct(Block b) {
        String cls = needsBlockClass(b) ? blockClassName(b) : b.shape.equals("pillar") ? "RotatedPillarBlock" : "Block";
        return "new " + cls + "(" + blockProps(b) + ")";
    }

    /** Model blocks with a custom outline or a facing direction get their own class. */
    public boolean needsBlockClass(Block b) {
        if (!b.shape.equals("model")) return false;
        return b.rotatable || shaped(b);
    }

    private boolean shaped(Block b) {
        Model m = ctx.p.model(b.model);
        return b.modelCollision && m != null && !Models.isFullCube(m);
    }

    public String blockClassName(Block b) {
        return Ids.toPascal(b.name) + "Block";
    }

    private static final String[] FACINGS = {"NORTH", "EAST", "SOUTH", "WEST"};

    /** Generates a block class with the model's outline and, when rotatable, a horizontal facing property. */
    public void blockClass(Block b) {
        String cls = blockClassName(b);
        Model m = ctx.p.model(b.model);
        boolean shaped = shaped(b), rot = b.rotatable;
        boolean codec = ctx.v.atLeast(McVersion.V1_20_4);
        Src s = new Src(ctx.pkg + ".block");
        s.imp("net.minecraft.world.level.block.Block", "net.minecraft.world.level.block.state.BlockBehaviour",
                "net.minecraft.world.level.block.state.BlockState");
        if (shaped) s.imp("net.minecraft.core.BlockPos", "net.minecraft.world.level.BlockGetter",
                "net.minecraft.world.phys.shapes.CollisionContext", "net.minecraft.world.phys.shapes.Shapes",
                "net.minecraft.world.phys.shapes.VoxelShape");
        if (rot) s.imp("net.minecraft.core.Direction", "net.minecraft.world.item.context.BlockPlaceContext",
                "net.minecraft.world.level.block.HorizontalDirectionalBlock", "net.minecraft.world.level.block.state.StateDefinition");
        if (rot && codec) s.imp("com.mojang.serialization.MapCodec");
        s.l("public class " + cls + " extends " + (rot ? "HorizontalDirectionalBlock" : "Block") + " {");
        if (rot && codec) s.l("public static final MapCodec<" + cls + "> CODEC = simpleCodec(" + cls + "::new);");
        if (shaped) {
            List<double[]> boxes = new ArrayList<>();
            for (double[] bx : Models.boxes(m)) {
                double[] c = new double[6];
                for (int i = 0; i < 6; i++) c[i] = Models.clamp(bx[i], 0, 16);
                if (c[3] > c[0] && c[4] > c[1] && c[5] > c[2]) boxes.add(c);
            }
            if (rot) {
                for (int q = 0; q < 4; q++) s.l("private static final VoxelShape SHAPE_" + FACINGS[q] + " = " + shapeExpr(boxes, q) + ";");
            } else s.l("private static final VoxelShape SHAPE = " + shapeExpr(boxes, 0) + ";");
        }
        s.blank();
        s.l("public " + cls + "(BlockBehaviour.Properties properties) {").l("super(properties);");
        if (rot) s.l("registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));");
        s.l("}");
        if (rot) {
            if (codec) s.blank().l("@Override").l("protected MapCodec<? extends HorizontalDirectionalBlock> codec() {").l("return CODEC;").l("}");
            s.blank().l("@Override").l("protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {")
                    .l("builder.add(FACING);").l("}");
            s.blank().l("@Override").l("public BlockState getStateForPlacement(BlockPlaceContext context) {")
                    .l("return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());").l("}");
        }
        if (shaped) {
            s.blank().l("@Override").l("public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {");
            if (rot) {
                s.l("return switch (state.getValue(FACING)) {").l("case EAST -> SHAPE_EAST;").l("case SOUTH -> SHAPE_SOUTH;")
                        .l("case WEST -> SHAPE_WEST;").l("default -> SHAPE_NORTH;").l("};");
            } else s.l("return SHAPE;");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "block/" + cls + ".java", s.toString());
    }

    /** Java expression combining the boxes (0..16 units) after turning them quarterTurns clockwise. */
    static String shapeExpr(List<double[]> boxes, int quarterTurns) {
        if (boxes.isEmpty()) return "Shapes.block()";
        List<String> parts = new ArrayList<>();
        for (double[] b : boxes) {
            double[] p1 = Models.rotateY(new double[]{b[0], b[1], b[2]}, quarterTurns);
            double[] p2 = Models.rotateY(new double[]{b[3], b[4], b[5]}, quarterTurns);
            parts.add("Block.box(" + d(Math.min(p1[0], p2[0])) + ", " + d(b[1]) + ", " + d(Math.min(p1[2], p2[2])) + ", "
                    + d(Math.max(p1[0], p2[0])) + ", " + d(b[4]) + ", " + d(Math.max(p1[2], p2[2])) + ")");
        }
        if (parts.size() == 1) return parts.get(0);
        return "Shapes.or(" + String.join(", ", parts) + ")";
    }

    private static String d(double v) {
        return v == Math.rint(v) ? (long) v + ".0" : String.valueOf(v);
    }

    public void importsForBlocks(Src s) {
        s.imp("net.minecraft.world.level.block.Block", "net.minecraft.world.level.block.SoundType",
                "net.minecraft.world.level.block.state.BlockBehaviour");
        for (Block b : ctx.all(Block.class)) {
            if (needsBlockClass(b)) s.imp(ctx.pkg + ".block." + blockClassName(b));
            else if (b.shape.equals("pillar")) s.imp("net.minecraft.world.level.block.RotatedPillarBlock");
        }
    }

    public void procedures() {
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.commands.CommandSourceStack", "net.minecraft.core.registries.BuiltInRegistries",
                "net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceLocation",
                "net.minecraft.server.MinecraftServer", "net.minecraft.tags.TagKey",
                "net.minecraft.world.entity.Entity", "net.minecraft.world.item.Item");
        s.l("/** Runs procedures compiled to functions in data/" + ctx.ns + "/" + ctx.funcDir() + ". */");
        s.l("public final class ModProcedures {");
        s.l("private ModProcedures() {").l("}");
        s.blank();
        s.l("public static void run(Entity entity, String function) {");
        s.l("if (entity.level().isClientSide()) return;");
        s.l("MinecraftServer server = entity.getServer();");
        s.l("if (server == null) return;");
        if (ctx.registryKeys()) {
            s.imp("net.minecraft.server.level.ServerLevel");
            s.l("CommandSourceStack source = entity.createCommandSourceStackForNameResolution((ServerLevel) entity.level()).withPermission(4).withSuppressedOutput();");
        } else {
            s.l("CommandSourceStack source = entity.createCommandSourceStack().withPermission(4).withSuppressedOutput();");
        }
        s.l("server.getCommands().performPrefixedCommand(source, \"function \" + " + ctx.mainClass + ".MODID + \":\" + function);");
        s.l("}");
        s.blank();
        s.l("public static void run(CommandSourceStack source, String function) {");
        s.l("source.getServer().getCommands().performPrefixedCommand(source.withPermission(4).withSuppressedOutput(), \"function \" + " + ctx.mainClass + ".MODID + \":\" + function);");
        s.l("}");
        s.blank();
        s.l("public static Item item(String id) {");
        s.l("return BuiltInRegistries.ITEM." + (ctx.registryKeys() ? "getValue(" : "get(") + GenContext.rlParse(ctx.singular(), "id") + ");");
        s.l("}");
        s.blank();
        s.l("public static TagKey<Item> itemTag(String id) {");
        s.l("return TagKey.create(Registries.ITEM, " + GenContext.rlParse(ctx.singular(), "id") + ");");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModProcedures.java", s.toString());
    }

    /** Condition expression testing an ItemStack variable named "stack". */
    public String stackMatches(String id) {
        String f = Ids.full(id);
        if (Ids.isTag(f)) return "stack.is(ModProcedures.itemTag(" + Src.str(f.substring(1)) + "))";
        return "stack.is(" + anyItemRef(f) + ")";
    }

    public boolean hasCommands() {
        return !ctx.all(Elements.Command.class).isEmpty() || new BridgeGen(ctx).modNeeded();
    }

    public void commands() {
        Src s = new Src(ctx.pkg);
        s.imp("com.mojang.brigadier.CommandDispatcher", "net.minecraft.commands.CommandSourceStack", "net.minecraft.commands.Commands");
        s.l("public final class ModCommands {");
        s.l("private ModCommands() {").l("}");
        s.blank();
        s.l("public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {");
        BridgeGen bridge = new BridgeGen(ctx);
        for (Elements.Command c : ctx.all(Elements.Command.class)) {
            if (!c.branches.isEmpty()) {
                BridgeGen.treeImports(s);
                for (String line : bridge.brigadier(c)) s.l(line);
                continue;
            }
            s.l("dispatcher.register(Commands.literal(" + Src.str(c.name) + ").requires(source -> source.hasPermission("
                    + Math.max(0, Math.min(4, c.permission)) + ")).executes(context -> {");
            s.l("ModProcedures.run(context.getSource(), " + Src.str("commands/" + c.name) + ");");
            s.l("return 1;");
            s.l("}));");
        }
        if (modstudio.gen.InterfaceGen.needsBridge(ctx.p)) s.l("ModBridge.register(dispatcher);");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModCommands.java", s.toString());
        bridge.mod();
    }

    /** itemKey/blockKey helpers on the main class for 1.21.2+ registry ids. */
    public static void registryKeyHelpers(GenContext ctx, Src s) {
        if (!ctx.registryKeys()) return;
        s.imp("net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceKey",
                "net.minecraft.world.item.Item", "net.minecraft.world.level.block.Block");
        s.l("public static ResourceKey<Item> itemKey(String path) {");
        s.l("return ResourceKey.create(Registries.ITEM, id(path));");
        s.l("}");
        s.blank();
        s.l("public static ResourceKey<Block> blockKey(String path) {");
        s.l("return ResourceKey.create(Registries.BLOCK, id(path));");
        s.l("}");
        s.blank();
    }

    public void writeIcon(String path) {
        TextureAsset t = ctx.p.texture(ctx.p.meta.icon);
        if (t != null) ctx.files.bytes(path, t.bytes());
    }

    /** Applies user code files for this target. */
    public static void codeFiles(GenContext ctx) {
        for (CodeFile cf : ctx.all(CodeFile.class)) {
            boolean match = cf.target.equals(ctx.target.id)
                    || (cf.target.equals("mods") && ctx.target.isMod());
            if (!match || cf.path.isBlank()) continue;
            String path = cf.path.replace("{package}", ctx.pkgPath).replace("{modid}", ctx.ns);
            String content = cf.content.replace("{package_name}", ctx.pkg).replace("{modid}", ctx.ns).replace("{main_class}", ctx.mainClass);
            ctx.files.text(path, content);
        }
    }
}
