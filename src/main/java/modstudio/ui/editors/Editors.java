package modstudio.ui.editors;

import javafx.scene.Node;
import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.ui.Workspace;

/** Creates the editor for an element and sets sensible defaults on new elements. */
public final class Editors {
    private Editors() {}

    public static Node create(Ctx c, Element e) {
        EditorBase<?> ed = switch (e) {
            case Item it -> new ItemEditor(c, it);
            case Block b -> new BlockEditor(c, b);
            case Fluid fl -> new GameplayEditors.FluidEditor(c, fl);
            case ArmorMaterial am -> new ArmorEditor(c, am);
            case ToolMaterial tm -> new GameplayEditors.ToolMaterialEditor(c, tm);
            case StatusEffect se -> new GameplayEditors.StatusEffectEditor(c, se);
            case Potion po -> new GameplayEditors.PotionEditor(c, po);
            case Enchantment en -> new GameplayEditors.EnchantmentEditor(c, en);
            case DamageType dt -> new GameplayEditors.DamageTypeEditor(c, dt);
            case Painting pa -> new GameplayEditors.PaintingEditor(c, pa);
            case Song so -> new GameplayEditors.SongEditor(c, so);
            case Elements.Model m -> new ModelEditor(c, m);
            case Systems.Shader sh -> new ShaderEditor(c, sh);
            case CreativeTab t -> new SimpleEditors.TabEditor(c, t);
            case Sound s -> new SimpleEditors.SoundEditor(c, s);
            case Recipe r -> new RecipeEditor(c, r);
            case LootTable l -> new LootTableEditor(c, l);
            case Advancement a -> new AdvancementEditor(c, a);
            case Tag t -> new SimpleEditors.TagEditor(c, t);
            case Function fn -> new SimpleEditors.FunctionEditor(c, fn);
            case Procedure p -> new SimpleEditors.ProcedureEditor(c, p);
            case Elements.Command cmd -> new SimpleEditors.CommandEditor(c, cmd);
            case CodeFile cf -> new SimpleEditors.CodeFileEditor(c, cf);
            case Systems.Mob m -> new SystemEditors.MobEditor(c, m);
            case Systems.Effect fx -> new SystemEditors.EffectEditor(c, fx);
            case Systems.Structure st -> new SystemEditors.StructureEditor(c, st);
            case Systems.Biome bi -> new SystemEditors.BiomeEditor(c, bi);
            case Systems.Dimension d -> new SystemEditors.DimensionEditor(c, d);
            case Systems.Menu m -> new InterfaceEditors.MenuEditor(c, m);
            case Systems.Hud h -> new InterfaceEditors.HudEditor(c, h);
            case Systems.Theme th -> new ThemeEditor(c, th);
            case Systems.IconFont fo -> new ScreenEditors.IconFontEditor(c, fo);
            case Systems.CustomScreen sc -> new ScreenEditors.ScreenEditor(c, sc);
            case Systems.ApiRequest r -> new InterfaceEditors.ApiRequestEditor(c, r);
            case Systems.Resource r -> new MachineEditors.ResourceEditor(c, r);
            case Systems.Machine m -> new MachineEditors.MachineEditor(c, m);
            case Systems.Multiblock mb -> new MachineEditors.MultiblockEditor(c, mb);
            case Systems.Cutscene cs -> new CutsceneEditor(c, cs);
            case Elements.Predicate pr -> new DataEditors.PredicateEditor(c, pr);
            case Elements.ItemModifier im -> new DataEditors.ItemModifierEditor(c, im);
            case Systems.Dialog d -> new DataEditors.DialogEditor(c, d);
            case Systems.Trader tr -> new DataEditors.TraderEditor(c, tr);
            case Systems.KeyBind k -> new DataEditors.KeyBindEditor(c, k);
            case ModElements.Container ct -> new AdvancedEditors.ContainerEditor(c, ct);
            case ModElements.CustomEntity ce -> new AdvancedEditors.CustomEntityEditor(c, ce);
            case ModElements.ModConfig cfg -> new AdvancedEditors.ModConfigEditor(c, cfg);
            case ModElements.MixinSpec mx -> new AdvancedEditors.MixinEditor(c, mx);
            case ModElements.Portal pt -> new AdvancedEditors.PortalEditor(c, pt);
            case ModElements.WorldFeature wf -> new AdvancedEditors.WorldFeatureEditor(c, wf);
            case ModElements.Profession pf -> new AdvancedEditors.ProfessionEditor(c, pf);
            case ModElements.ArmorTrim at -> new AdvancedEditors.ArmorTrimEditor(c, at);
            case ModElements.BannerPattern bp -> new AdvancedEditors.BannerPatternEditor(c, bp);
            case ModElements.CustomRecipeType rt -> new AdvancedEditors.RecipeTypeEditor(c, rt);
            default -> throw new IllegalArgumentException(e.type().id);
        };
        return ed.build();
    }

    public static void initDefaults(Workspace ws, Element e) {
        if (ws != null) ElementDefaults.init(ws.project, e);
    }
}
