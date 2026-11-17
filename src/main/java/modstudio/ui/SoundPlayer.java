package modstudio.ui;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import modstudio.core.Element;
import modstudio.core.Elements;
import modstudio.core.Ids;
import modstudio.core.Images;
import modstudio.core.VanillaSounds;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Plays Ogg Vorbis sounds for previews: the project's own sound files and vanilla sound events from the installed
 * game. One sound plays at a time; starting another stops the first.
 */
public final class SoundPlayer {
    private static final ReadOnlyStringWrapper PLAYING = new ReadOnlyStringWrapper("");
    private static volatile Thread thread;
    private static volatile boolean stop;

    private SoundPlayer() {}

    /** Key of the sound now playing (empty when silent), for play / stop buttons. */
    public static ReadOnlyStringProperty playing() {
        return PLAYING.getReadOnlyProperty();
    }

    /**
     * Plays a sound event id: a project sound (its first file) or a vanilla event. Returns false when the sound
     * cannot be found.
     */
    public static boolean playEvent(Workspace ws, String id) {
        byte[] ogg = eventBytes(ws, id);
        if (ogg == null) return false;
        play(ogg, id);
        return true;
    }

    public static byte[] eventBytes(Workspace ws, String id) {
        if (id == null || id.isBlank()) return null;
        String full = Ids.full(id.trim());
        if (Ids.ns(full).equals(ws.ns())) {
            for (Element e : ws.project.elements) {
                if (e instanceof Elements.Sound s && s.name.equals(Ids.path(full)) && !s.files.isEmpty()) return Images.decodeBase64(s.files.get(0).data);
            }
            return null;
        }
        VanillaSounds vs = VanillaSounds.get();
        return vs == null ? null : vs.event(full);
    }

    /** Starts playing Ogg bytes; key names the sound for {@link #playing()}. */
    public static synchronized void play(byte[] ogg, String key) {
        stop();
        stop = false;
        Thread t = new Thread(() -> {
            try {
                playPcm(ogg);
            } catch (Exception ignored) {
                // a broken file just stays silent
            } finally {
                if (thread == Thread.currentThread()) Platform.runLater(() -> PLAYING.set(""));
            }
        }, "sound-preview");
        t.setDaemon(true);
        thread = t;
        PLAYING.set(key);
        t.start();
    }

    public static synchronized void stop() {
        stop = true;
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
        PLAYING.set("");
    }

    /** Toggles: stops the sound when it is the one playing, otherwise plays it. */
    public static boolean toggleEvent(Workspace ws, String id) {
        if (!id.isBlank() && id.equals(PLAYING.get())) {
            stop();
            return true;
        }
        return playEvent(ws, id);
    }

    /** Receives decoded audio: the format once, then 16-bit little-endian PCM. */
    public interface Sink {
        void open(float rate, int channels) throws Exception;

        void write(byte[] pcm, int length) throws Exception;
    }

    private static void playPcm(byte[] ogg) throws Exception {
        SourceDataLine[] line = new SourceDataLine[1];
        try {
            decode(new ByteArrayInputStream(ogg), new Sink() {
                @Override
                public void open(float rate, int channels) throws Exception {
                    AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
                    line[0] = AudioSystem.getSourceDataLine(format);
                    line[0].open(format);
                    line[0].start();
                }

                @Override
                public void write(byte[] pcm, int length) {
                    line[0].write(pcm, 0, length);
                }
            });
            if (line[0] != null && !stop) line[0].drain();
        } finally {
            if (line[0] != null) {
                line[0].stop();
                line[0].close();
            }
        }
    }

    /** Decodes Ogg Vorbis with JOrbis into the sink until the end or until stopped. */
    public static void decode(InputStream in, Sink sink) throws Exception {
        SyncState oy = new SyncState();
        StreamState os = new StreamState();
        Page og = new Page();
        Packet op = new Packet();
        Info vi = new Info();
        Comment vc = new Comment();
        DspState vd = new DspState();
        Block vb = new Block(vd);
        oy.init();
        int index = oy.buffer(4096);
        int bytes = in.read(oy.data, index, 4096);
        oy.wrote(Math.max(0, bytes));
        if (oy.pageout(og) != 1) return;
        os.init(og.serialno());
        vi.init();
        vc.init();
        if (os.pagein(og) < 0 || os.packetout(op) != 1 || vi.synthesis_headerin(vc, op) < 0) return;
        int headers = 0;
        while (headers < 2) {
            while (headers < 2) {
                int result = oy.pageout(og);
                if (result == 0) break;
                if (result == 1) {
                    os.pagein(og);
                    while (headers < 2) {
                        result = os.packetout(op);
                        if (result == 0) break;
                        if (result == -1) return;
                        vi.synthesis_headerin(vc, op);
                        headers++;
                    }
                }
            }
            index = oy.buffer(4096);
            bytes = in.read(oy.data, index, 4096);
            if (bytes <= 0 && headers < 2) return;
            oy.wrote(Math.max(0, bytes));
        }
        int channels = vi.channels;
        sink.open(vi.rate, channels);
        try {
            int convsize = 4096 / channels;
            byte[] conv = new byte[8192];
            vd.synthesis_init(vi);
            vb.init(vd);
            float[][][] pcmHolder = new float[1][][];
            int[] offsets = new int[channels];
            boolean eos = false;
            while (!eos && !stop) {
                while (!eos && !stop) {
                    int result = oy.pageout(og);
                    if (result == 0) break;
                    if (result == -1) continue;
                    os.pagein(og);
                    while (!stop) {
                        result = os.packetout(op);
                        if (result == 0) break;
                        if (result == -1) continue;
                        if (vb.synthesis(op) == 0) vd.synthesis_blockin(vb);
                        int samples;
                        while ((samples = vd.synthesis_pcmout(pcmHolder, offsets)) > 0 && !stop) {
                            float[][] pcm = pcmHolder[0];
                            int out = Math.min(samples, convsize);
                            for (int ch = 0; ch < channels; ch++) {
                                int ptr = ch * 2;
                                int at = offsets[ch];
                                for (int j = 0; j < out; j++) {
                                    int v = (int) (pcm[ch][at + j] * 32767.0);
                                    v = Math.max(-32768, Math.min(32767, v));
                                    conv[ptr] = (byte) v;
                                    conv[ptr + 1] = (byte) (v >>> 8);
                                    ptr += 2 * channels;
                                }
                            }
                            sink.write(conv, 2 * channels * out);
                            vd.synthesis_read(out);
                        }
                    }
                    if (og.eos() != 0) eos = true;
                }
                if (!eos) {
                    index = oy.buffer(4096);
                    bytes = in.read(oy.data, index, 4096);
                    if (bytes <= 0) eos = true;
                    else oy.wrote(bytes);
                }
            }
        } finally {
            os.clear();
            vb.clear();
            vd.clear();
            vi.clear();
            oy.clear();
        }
    }
}
