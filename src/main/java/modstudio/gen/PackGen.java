package modstudio.gen;

import modstudio.core.Elements;
import modstudio.core.TextureAsset;

/** Plain data packs and resource packs. */
public final class PackGen {
    private PackGen() {}

    public static void datapack(GenContext ctx) {
        ctx.files.json("pack.mcmeta", J.obj("pack", J.obj("pack_format", ctx.v.dataFormat,
                "description", ctx.p.meta.name + (ctx.p.meta.description.isBlank() ? "" : " - " + ctx.p.meta.description))));
        icon(ctx);
        new DataGen(ctx, "", false, false).generate(true);
        if (!ctx.all(Elements.Item.class).isEmpty()) {
            ctx.note("items are provided as custom-model-data items: /function " + ctx.ns + ":give/<item>; textures come from the resource pack");
        }
        for (Elements.Block b : ctx.all(Elements.Block.class)) ctx.note("block " + b.name + " is not supported by data packs");
        JavaCommon.codeFiles(ctx);
    }

    public static void resourcepack(GenContext ctx) {
        ctx.files.json("pack.mcmeta", J.obj("pack", J.obj("pack_format", ctx.v.resourceFormat,
                "description", ctx.p.meta.name + " resources")));
        icon(ctx);
        new AssetGen(ctx, "").customModelDataAssets();
        JavaCommon.codeFiles(ctx);
    }

    private static void icon(GenContext ctx) {
        TextureAsset t = ctx.p.texture(ctx.p.meta.icon);
        if (t != null) ctx.files.bytes("pack.png", t.bytes());
    }
}
