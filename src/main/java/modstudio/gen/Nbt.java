package modstudio.gen;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Minimal NBT writer for structure template files (gzip compressed, big endian named tags). */
public final class Nbt {
    private Nbt() {}

    public static final class Compound {
        final Map<String, Object> values = new LinkedHashMap<>();

        public Compound put(String key, Object value) {
            values.put(key, value);
            return this;
        }
    }

    /** A list whose element type is given explicitly so empty lists are typed correctly. */
    public record TypedList(byte type, List<?> items) {}

    public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, STRING = 8, LIST = 9, COMPOUND = 10;

    public static TypedList ints(int... v) {
        List<Integer> l = new ArrayList<>();
        for (int i : v) l.add(i);
        return new TypedList(INT, l);
    }

    public static TypedList compounds(List<Compound> l) {
        return new TypedList(COMPOUND, l);
    }

    /** Reads a gzip compressed NBT file; compounds become Compound, lists TypedList, numbers boxed values. */
    public static Compound read(byte[] gz) throws IOException {
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz)))) {
            byte t = in.readByte();
            if (t != COMPOUND) throw new IOException("Not an NBT compound");
            in.readUTF();
            return readCompound(in);
        }
    }

    private static Compound readCompound(java.io.DataInputStream in) throws IOException {
        Compound c = new Compound();
        while (true) {
            byte t = in.readByte();
            if (t == END) return c;
            String name = in.readUTF();
            c.put(name, readPayload(in, t));
        }
    }

    private static Object readPayload(java.io.DataInputStream in, byte t) throws IOException {
        return switch (t) {
            case BYTE -> in.readByte();
            case SHORT -> in.readShort();
            case INT -> in.readInt();
            case LONG -> in.readLong();
            case FLOAT -> in.readFloat();
            case DOUBLE -> in.readDouble();
            case STRING -> in.readUTF();
            case LIST -> {
                byte et = in.readByte();
                int n = in.readInt();
                List<Object> l = new ArrayList<>();
                for (int i = 0; i < n; i++) l.add(readPayload(in, et));
                yield new TypedList(et, l);
            }
            case COMPOUND -> readCompound(in);
            case 7 -> {
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                yield b;
            }
            case 11 -> {
                int n = in.readInt();
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                yield a;
            }
            case 12 -> {
                int n = in.readInt();
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                yield a;
            }
            default -> throw new IOException("Unknown NBT tag " + t);
        };
    }

    public static Object get(Compound c, String key) {
        return c.values.get(key);
    }

    /** Fills a volume from a structure template file; returns false when the file is not a structure. */
    public static boolean importStructure(byte[] gz, modstudio.core.Systems.Volume v) throws IOException {
        Compound root = read(gz);
        if (!(get(root, "size") instanceof TypedList size) || !(get(root, "palette") instanceof TypedList pal)
                || !(get(root, "blocks") instanceof TypedList blocks)) return false;
        int sx = (Integer) size.items().get(0), sy = (Integer) size.items().get(1), sz = (Integer) size.items().get(2);
        if (sx > 48 || sy > 48 || sz > 48) throw new IOException("Structures up to 48 x 48 x 48 can be edited");
        List<String> palette = new ArrayList<>();
        for (Object o : pal.items()) {
            Compound p = (Compound) o;
            StringBuilder sb = new StringBuilder(String.valueOf(get(p, "Name")));
            if (get(p, "Properties") instanceof Compound props && !props.values.isEmpty()) {
                sb.append('[');
                boolean first = true;
                for (var e : props.values.entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(e.getKey()).append('=').append(e.getValue());
                    first = false;
                }
                sb.append(']');
            }
            palette.add(sb.toString());
        }
        v.sx = sx;
        v.sy = sy;
        v.sz = sz;
        v.palette = palette;
        v.cells = modstudio.core.Systems.Volume.filled(sx * sy * sz);
        for (Object o : blocks.items()) {
            Compound b = (Compound) o;
            TypedList pos = (TypedList) get(b, "pos");
            int x = (Integer) pos.items().get(0), y = (Integer) pos.items().get(1), z = (Integer) pos.items().get(2);
            v.set(x, y, z, (Integer) get(b, "state"));
        }
        return true;
    }

    public static byte[] gzip(Compound root) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
                out.writeByte(COMPOUND);
                string(out, "");
                compound(out, root);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte typeOf(Object v) {
        if (v instanceof Byte || v instanceof Boolean) return BYTE;
        if (v instanceof Short) return SHORT;
        if (v instanceof Integer) return INT;
        if (v instanceof Long) return LONG;
        if (v instanceof Float) return FLOAT;
        if (v instanceof Double) return DOUBLE;
        if (v instanceof String) return STRING;
        if (v instanceof TypedList) return LIST;
        if (v instanceof Compound) return COMPOUND;
        throw new IllegalArgumentException("Unsupported NBT value " + v);
    }

    private static void string(DataOutputStream out, String s) throws IOException {
        out.writeUTF(s);
    }

    private static void compound(DataOutputStream out, Compound c) throws IOException {
        for (var e : c.values.entrySet()) {
            byte t = typeOf(e.getValue());
            out.writeByte(t);
            string(out, e.getKey());
            payload(out, t, e.getValue());
        }
        out.writeByte(END);
    }

    private static void payload(DataOutputStream out, byte t, Object v) throws IOException {
        switch (t) {
            case BYTE -> out.writeByte(v instanceof Boolean b ? (b ? 1 : 0) : (Byte) v);
            case SHORT -> out.writeShort((Short) v);
            case INT -> out.writeInt((Integer) v);
            case LONG -> out.writeLong((Long) v);
            case FLOAT -> out.writeFloat((Float) v);
            case DOUBLE -> out.writeDouble((Double) v);
            case STRING -> string(out, (String) v);
            case LIST -> {
                TypedList l = (TypedList) v;
                out.writeByte(l.items().isEmpty() ? END : l.type());
                out.writeInt(l.items().size());
                for (Object o : l.items()) payload(out, l.type(), o);
            }
            case COMPOUND -> compound(out, (Compound) v);
            default -> throw new IllegalArgumentException();
        }
    }
}
