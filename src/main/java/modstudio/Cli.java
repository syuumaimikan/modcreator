package modstudio;

import modstudio.build.BuildService;
import modstudio.core.*;
import modstudio.gen.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Headless commands: --generate <outDir> [project.msproj] [mcVersions,...] [targets,...], --build <dir> [tasks...], --sample <file>, --validate [file]. */
public final class Cli {
    private Cli() {}

    public static boolean handles(String arg) {
        return arg.equals("--generate") || arg.equals("--build") || arg.equals("--sample") || arg.equals("--validate");
    }

    public static int run(String[] args) {
        try {
            switch (args[0]) {
                case "--sample" -> {
                    Project p = SampleProject.create();
                    ProjectIO.save(p, Path.of(args[1]));
                    System.out.println("Wrote " + args[1]);
                }
                case "--validate" -> {
                    Project p = args.length > 1 ? ProjectIO.load(Path.of(args[1])) : SampleProject.create();
                    for (var pr : Validator.validate(p)) System.out.println(pr.severity() + " " + pr.message());
                }
                case "--generate" -> {
                    Path out = Path.of(args[1]);
                    Project p = args.length > 2 ? ProjectIO.load(Path.of(args[2])) : SampleProject.create();
                    if (args.length > 3) {
                        p.meta.mcVersions = new java.util.ArrayList<>(Arrays.asList(args[3].split(",")));
                        p.meta.mcVersion = p.meta.mcVersions.get(p.meta.mcVersions.size() - 1);
                        p.normalizeVersions();
                    }
                    p.meta.targets = args.length > 4 ? Arrays.asList(args[4].split(",")) : new java.util.ArrayList<>(
                            Arrays.stream(Target.values()).map(t -> t.id).toList());
                    Map<Target, Map<McVersion, FileMap>> packs = new EnumMap<>(Target.class);
                    for (Build b : p.builds()) {
                        GenContext ctx = Generators.generate(p, b);
                        if (b.target() == Target.DATAPACK || b.target() == Target.RESOURCEPACK)
                            packs.computeIfAbsent(b.target(), k -> new TreeMap<>()).put(b.mc(), ctx.files);
                        Exporter.writeFolder(ctx.files, out.resolve(b.key()));
                        if (!b.target().usesGradle()) Exporter.writeZip(ctx.files, out.resolve("dist").resolve(Exporter.packFileName(p, b)));
                        System.out.println(b.key() + ": " + ctx.files.size() + " files");
                        for (String n : ctx.notes) System.out.println("  note: " + n);
                    }
                    for (var en : packs.entrySet()) {
                        FileMap all = Exporter.universalPack(en.getValue(), en.getKey());
                        if (all == null) continue;
                        Path zip = out.resolve("dist").resolve(Exporter.universalFileName(p, en.getKey()));
                        Exporter.writeZip(all, zip);
                        System.out.println("universal " + en.getKey().id + ": " + all.size() + " files -> " + zip.getFileName());
                    }
                }
                case "--build" -> {
                    Path dir = Path.of(args[1]);
                    List<String> tasks = args.length > 2 ? Arrays.asList(args).subList(2, args.length) : List.of("build");
                    Path jdk = BuildService.findJavaHome(null);
                    System.out.println("JDK: " + jdk);
                    BuildService.run(dir, jdk, tasks, List.of(), System.out::println, null);
                    System.out.println("Artifact: " + BuildService.findJar(dir));
                    if (!Files.exists(dir)) return 1;
                }
                default -> {
                    return 2;
                }
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }
}
