package modstudio;

import modstudio.build.BuildService;
import modstudio.core.*;
import modstudio.gen.*;
import modstudio.mcp.McpServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Headless commands: --generate <outDir> [project.msproj] [mcVersions,...] [targets,...], --import <artifact> <out.msproj>, --build <dir> [tasks...], --sample <file>, --sample-rpg <file>, --validate [file]. */
public final class Cli {
    private Cli() {}

    public static boolean handles(String arg) {
        return arg.equals("--generate") || arg.equals("--import") || arg.equals("--build") || arg.equals("--upgrade")
                || arg.equals("--sample") || arg.equals("--sample-rpg") || arg.equals("--validate") || arg.equals("--mcp");
    }

    public static int run(String[] args) {
        if (args == null || args.length == 0) return 2;
        try {
            switch (args[0]) {
                case "--mcp" -> {
                    Path proj = null;
                    boolean ro = false;
                    for (int i = 1; i < args.length; i++) {
                        if ("--readonly".equals(args[i])) ro = true;
                        else if (proj == null) proj = Path.of(args[i]);
                    }
                    return McpServer.run(proj, ro);
                }
                case "--sample-rpg" -> {
                    Project p = RpgSampleProject.create();
                    Path out = Path.of(args.length > 1 ? args[1] : "aetheria.msproj");
                    if (out.getParent() != null) Files.createDirectories(out.getParent());
                    ProjectIO.save(p, out);
                    System.out.println("Wrote RPG sample mod project to " + out);
                }
                case "--sample" -> {
                    if (args.length < 2) { System.err.println("Usage: --sample <out.msproj>"); return 2; }
                    Project p = SampleProject.create();
                    ProjectIO.save(p, Path.of(args[1]));
                    System.out.println("Wrote " + args[1]);
                }
                case "--import" -> {
                    if (args.length < 3) { System.err.println("Usage: --import <artifact> <out.msproj>"); return 2; }
                    ProjectImporter.Report r = ProjectImporter.importArtifact(Path.of(args[1]));
                    Path outFile = Path.of(args[2]);
                    ProjectIO.save(r.project(), outFile);
                    System.out.println("Imported " + args[1] + " -> " + outFile + " (" + ProjectScope.kind(r.project()) + ", " + r.counts() + ")");
                    for (String n : r.notes()) System.out.println("  note: " + n);
                }
                case "--upgrade" -> {
                    if (args.length < 4) { System.err.println("Usage: --upgrade <in.msproj> <out.msproj> <mcVersion> [targets,...]"); return 2; }
                    Project p = ProjectIO.load(Path.of(args[1]));
                    McVersion targetMc = McVersion.orNull(args[3]);
                    if (targetMc == null) { System.err.println("Unknown Minecraft version: " + args[3]); return 2; }
                    List<Target> targets = args.length > 4
                            ? Arrays.stream(args[4].split(",")).map(String::trim).map(Target::of).filter(java.util.Objects::nonNull).toList()
                            : List.of();
                    ProjectUpgrader.Report rep = ProjectUpgrader.upgrade(p, targetMc, targets);
                    ProjectIO.save(p, Path.of(args[2]));
                    for (String c : rep.changes()) System.out.println("  " + c);
                    for (String w : rep.warnings()) System.err.println("  warning: " + w);
                }
                case "--validate" -> {
                    Project p = args.length > 1 ? ProjectIO.load(Path.of(args[1])) : SampleProject.create();
                    for (var pr : Validator.validate(p)) System.out.println(pr.severity() + " " + pr.message());
                }
                case "--generate" -> {
                    if (args.length < 2) { System.err.println("Usage: --generate <outDir> [project.msproj] [mcVersions,...] [targets,...]"); return 2; }
                    Path out = Path.of(args[1]);
                    Project p = args.length > 2 ? ProjectIO.load(Path.of(args[2])) : SampleProject.create();
                    if (args.length > 3) {
                        p.meta.mcVersions = new java.util.ArrayList<>(Arrays.asList(args[3].split(",")));
                        p.meta.mcVersion = p.meta.mcVersions.get(p.meta.mcVersions.size() - 1);
                        p.normalizeVersions();
                    }
                    p.meta.targets = args.length > 4
                            ? new java.util.ArrayList<>(Arrays.asList(args[4].split(",")))
                            : new java.util.ArrayList<>(Arrays.stream(Target.values()).map(t -> t.id).toList());
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
                    if (args.length < 2) { System.err.println("Usage: --build <dir> [tasks...]"); return 2; }
                    Path dir = Path.of(args[1]);
                    if (!Files.isDirectory(dir)) { System.err.println("Build directory not found: " + dir); return 1; }
                    List<String> tasks = args.length > 2 ? Arrays.asList(args).subList(2, args.length) : List.of("build");
                    Path jdk = BuildService.findJavaHomeForProject(dir, null);
                    System.out.println("JDK: " + jdk);
                    BuildService.run(dir, jdk, tasks, List.of(), System.out::println, null);
                    System.out.println("Artifact: " + BuildService.findJar(dir));
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
