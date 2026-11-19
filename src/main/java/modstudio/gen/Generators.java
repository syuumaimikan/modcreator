package modstudio.gen;

import modstudio.core.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Entry point: generates the complete file set for one target. */
public final class Generators {
    private Generators() {}

    private static final List<Consumer<GenContext>> CONTRIBUTORS = new CopyOnWriteArrayList<>();

    /** Registers a plugin contributor invoked after core file generation for every target. */
    public static void registerContributor(Consumer<GenContext> contributor) {
        if (contributor != null) CONTRIBUTORS.add(contributor);
    }

    public static GenContext generate(Project p, Build b) {
        return generate(p, b.target(), b.mc());
    }

    public static GenContext generate(Project p, Target t, McVersion v) {
        GenContext ctx = new GenContext(p, t, v);
        switch (t) {
            case FORGE -> new ForgeLikeGen(ctx, false).generate();
            case NEOFORGE -> new ForgeLikeGen(ctx, true).generate();
            case FABRIC, QUILT -> new FabricGen(ctx).generate();
            case PAPER, FOLIA -> new PaperGen(ctx).generate();
            case DATAPACK -> PackGen.datapack(ctx);
            case RESOURCEPACK -> PackGen.resourcepack(ctx);
            case BEDROCK -> new BedrockGen(ctx).generate();
        }
        for (var c : CONTRIBUTORS) c.accept(ctx);
        return ctx;
    }
}
