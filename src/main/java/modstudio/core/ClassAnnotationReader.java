package modstudio.core;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight JVM .class parser that extracts interface flags and SpongePowered Mixin metadata
 * (@Mixin targets/values, @Inject method names, @Accessor/@Invoker presence) without external bytecode libs.
 */
public final class ClassAnnotationReader {
    private ClassAnnotationReader() {}

    public record MixinInfo(boolean isInterface, List<String> targets, List<String> targetStrings,
                            List<String> injectMethods, boolean hasAccessors) {}

    private static final int ACC_INTERFACE = 0x0200;

    public static MixinInfo inspectMixin(byte[] classBytes) {
        if (classBytes == null || classBytes.length < 24) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            if (in.readInt() != 0xCAFEBABE) return null;
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int cpCount = in.readUnsignedShort();
            String[] utf8 = new String[cpCount];
            int[] classNameIdx = new int[cpCount];
            for (int i = 1; i < cpCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[i] = new String(in.readNBytes(in.readUnsignedShort()), StandardCharsets.UTF_8);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        i++;
                    }
                    case 7 -> classNameIdx[i] = in.readUnsignedShort();
                    case 8, 16, 19, 20 -> in.skipBytes(2);
                    case 15 -> in.skipBytes(3);
                    default -> {
                        return null;
                    }
                }
            }
            int accessFlags = in.readUnsignedShort();
            boolean isInterface = (accessFlags & ACC_INTERFACE) != 0;
            in.readUnsignedShort(); // this_class
            in.readUnsignedShort(); // super_class
            int ifaces = in.readUnsignedShort();
            in.skipBytes(ifaces * 2);

            List<String> injectMethods = new ArrayList<>();
            boolean[] hasAccessors = {false};

            int fields = in.readUnsignedShort();
            for (int i = 0; i < fields; i++) {
                in.skipBytes(6);
                skipAttributes(in);
            }
            int methods = in.readUnsignedShort();
            for (int i = 0; i < methods; i++) {
                in.skipBytes(6);
                readMemberAttributes(in, utf8, injectMethods, hasAccessors);
            }
            List<String> targets = new ArrayList<>();
            List<String> targetStrings = new ArrayList<>();
            int classAttrs = in.readUnsignedShort();
            for (int i = 0; i < classAttrs; i++) {
                String attr = cpStr(utf8, in.readUnsignedShort());
                int len = in.readInt();
                if ("RuntimeVisibleAnnotations".equals(attr) || "RuntimeInvisibleAnnotations".equals(attr)) {
                    byte[] data = in.readNBytes(len);
                    parseClassAnnotations(data, utf8, classNameIdx, targets, targetStrings);
                } else {
                    in.skipBytes(len);
                }
            }
            return new MixinInfo(isInterface, targets, targetStrings, injectMethods, hasAccessors[0]);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String cpStr(String[] utf8, int idx) {
        return idx > 0 && idx < utf8.length && utf8[idx] != null ? utf8[idx] : "";
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.readUnsignedShort();
            in.skipBytes(in.readInt());
        }
    }

    private static void readMemberAttributes(DataInputStream in, String[] utf8, List<String> injectMethods,
                                             boolean[] hasAccessors) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String attr = cpStr(utf8, in.readUnsignedShort());
            int len = in.readInt();
            if ("RuntimeVisibleAnnotations".equals(attr) || "RuntimeInvisibleAnnotations".equals(attr)) {
                parseMethodAnnotations(in.readNBytes(len), utf8, injectMethods, hasAccessors);
            } else {
                in.skipBytes(len);
            }
        }
    }

    private static void parseClassAnnotations(byte[] data, String[] utf8, int[] classNameIdx,
                                              List<String> targets, List<String> targetStrings) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                String type = cpStr(utf8, in.readUnsignedShort());
                int pairs = in.readUnsignedShort();
                boolean isMixin = "Lorg/spongepowered/asm/mixin/Mixin;".equals(type);
                for (int p = 0; p < pairs; p++) {
                    String name = cpStr(utf8, in.readUnsignedShort());
                    readElementValue(in, utf8, val -> {
                        if (!isMixin) return;
                        if (val.kind == 'c') {
                            String cls = internalFromDescriptor(val.str);
                            if (!cls.isBlank() && !targets.contains(cls)) targets.add(cls);
                        } else if (val.kind == 's' && "targets".equals(name)) {
                            String cls = val.str.replace('/', '.');
                            if (!cls.isBlank() && !targetStrings.contains(cls)) targetStrings.add(cls);
                        }
                    });
                }
            }
        }
    }

    private static void parseMethodAnnotations(byte[] data, String[] utf8, List<String> injectMethods,
                                               boolean[] hasAccessors) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int n = in.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                String type = cpStr(utf8, in.readUnsignedShort());
                if (type.startsWith("Lorg/spongepowered/asm/mixin/gen/")) hasAccessors[0] = true;
                boolean isHook = type.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")
                        || type.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")
                        || type.equals("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;")
                        || type.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")
                        || type.equals("Lorg/spongepowered/asm/mixin/injection/ModifyReturnValue;");
                int pairs = in.readUnsignedShort();
                for (int p = 0; p < pairs; p++) {
                    String name = cpStr(utf8, in.readUnsignedShort());
                    readElementValue(in, utf8, val -> {
                        if (isHook && "method".equals(name) && val.kind == 's' && !val.str.isBlank()) {
                            String m = val.str;
                            int paren = m.indexOf('(');
                            if (paren > 0) m = m.substring(0, paren);
                            if (!injectMethods.contains(m)) injectMethods.add(m);
                        }
                    });
                }
            }
        }
    }

    private record ElemVal(char kind, String str) {}

    private static void readElementValue(DataInputStream in, String[] utf8,
                                         java.util.function.Consumer<ElemVal> out) throws IOException {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> in.skipBytes(2);
            case 's' -> out.accept(new ElemVal('s', cpStr(utf8, in.readUnsignedShort())));
            case 'c' -> out.accept(new ElemVal('c', cpStr(utf8, in.readUnsignedShort())));
            case 'e' -> in.skipBytes(4);
            case '@' -> {
                in.readUnsignedShort();
                int pairs = in.readUnsignedShort();
                for (int p = 0; p < pairs; p++) {
                    in.readUnsignedShort();
                    readElementValue(in, utf8, out);
                }
            }
            case '[' -> {
                int len = in.readUnsignedShort();
                for (int i = 0; i < len; i++) readElementValue(in, utf8, out);
            }
            default -> throw new IOException("Unknown annotation tag " + tag);
        }
    }

    private static String internalFromDescriptor(String desc) {
        if (desc == null || !desc.startsWith("L") || !desc.endsWith(";")) return "";
        return desc.substring(1, desc.length() - 1).replace('/', '.');
    }
}

