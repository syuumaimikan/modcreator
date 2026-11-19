package modstudio.core;

import modstudio.core.Elements.*;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Walks all {@link Step} lists (and nested {@code then} / {@code orElse} branches) across any {@link Element}
 * or {@link Project}. Keeps validation, refactoring, upgrade, Java step collection, and compatibility checks
 * consistent whenever new element types with procedure hooks are added.
 */
public final class StepWalker {
    private StepWalker() {}

    /** Visits every {@link Step} inside the project alongside its owning {@link Element}. */
    public static void walk(Project p, BiConsumer<Element, Step> visitor) {
        if (p == null || visitor == null) return;
        for (Element e : p.elements) {
            if (e != null) walkElement(e, step -> visitor.accept(e, step));
        }
    }

    /** Visits every {@link Step} owned by {@code element} (including nested branches). */
    public static void walkElement(Element element, Consumer<Step> visitor) {
        if (element == null || visitor == null) return;
        switch (element) {
            case Item it -> walkAll(visitor, it.onUse, it.onHitEntity, it.onTickInHand);
            case Block b -> walkAll(visitor, b.onUse, b.onStep, b.onRandomTick, b.onNeighborChanged);
            case Fluid fl -> walkList(fl.onEntityInside, visitor);
            case StatusEffect se -> walkList(se.onTick, visitor);
            case Enchantment en -> walkAll(visitor, en.onHit, en.onTick);
            case Procedure pr -> walkList(pr.steps, visitor);
            case Command c -> {
                walkList(c.steps, visitor);
                for (CommandBranch b : c.branches) if (b != null) walkList(b.steps, visitor);
            }
            case ModElements.Container ct -> walkAll(visitor, ct.onOpen, ct.onClose, ct.onCraft);
            case ModElements.CustomEntity ce -> walkAll(visitor, ce.onSpawn, ce.onHurt, ce.onDeath, ce.onInteract);
            case Systems.Mob m -> {
                walkAll(visitor, m.onSpawn, m.onTick, m.onHurt, m.onDeath, m.onInteract);
                for (Systems.BossPhase ph : m.phases) if (ph != null) walkList(ph.steps, visitor);
            }
            case Systems.Menu menu -> {
                for (Systems.MenuSlot sl : menu.slots) if (sl != null) walkList(sl.steps, visitor);
            }
            case Systems.Dialog d -> {
                for (Systems.DialogButton btn : d.buttons) if (btn != null) walkList(btn.steps, visitor);
            }
            case Systems.Hud h -> walkList(h.onFinish, visitor);
            case Systems.ApiRequest r -> walkAll(visitor, r.onSuccess, r.onClientError, r.onServerError, r.onFailure);
            case Systems.Cutscene c -> walkList(c.onEnd, visitor);
            case Systems.KeyBind kb -> walkList(kb.steps, visitor);
            case Systems.Resource res -> walkList(res.onPollution, visitor);
            case Systems.Machine mach -> walkAll(visitor, mach.onCycle, mach.onPlace, mach.onBreak, mach.onOverload);
            case Systems.Multiblock mb -> walkAll(visitor, mb.onAssemble, mb.onUse, mb.onFinish, mb.onDisassemble);
            default -> {
            }
        }
    }

    /** Visits every {@link Step} list recursively across {@code then} and {@code orElse}. */
    @SafeVarargs
    public static void walkAll(Consumer<Step> visitor, List<Step>... lists) {
        if (lists == null || visitor == null) return;
        for (List<Step> list : lists) walkList(list, visitor);
    }

    /** Visits a single {@link Step} list and all nested {@code then}/{@code orElse} branches. */
    public static void walkList(List<Step> list, Consumer<Step> visitor) {
        if (list == null || visitor == null) return;
        for (Step s : list) {
            if (s == null) continue;
            visitor.accept(s);
            walkList(s.then, visitor);
            walkList(s.orElse, visitor);
        }
    }
}
