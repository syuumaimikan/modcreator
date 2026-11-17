package modstudio.gen;

import modstudio.core.*;


/** Entry point: generates the complete file set for one target. */
public final class Generators {
    private Generators() {}

    public static GenContext generate(Project p, Build b) {
        return generate(p, b.target(), b.mc());
    }

    public static GenContext generate(Project p, Target t, McVersion v) {
        GenContext ctx = new GenContext(p, t, v);
        switch (t) {
            case FORGE -> new ForgeLikeGen(ctx, false).generate();
            case NEOFORGE -> new ForgeLikeGen(ctx, true).generate();
            case FABRIC -> new FabricGen(ctx).generate();
            case PAPER -> new PaperGen(ctx).generate();
            case DATAPACK -> PackGen.datapack(ctx);
            case RESOURCEPACK -> PackGen.resourcepack(ctx);
            case BEDROCK -> new BedrockGen(ctx).generate();
        }
        return ctx;
    }
}
