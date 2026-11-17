package modstudio.core;

import java.util.*;

/** Collects the translation keys a project produces and resolves texts per game language. */
public final class Localization {
    private Localization() {}

    public record Entry(String key, Element element, String field, String english) {}

    public static final List<String> GAME_LANGUAGES = List.of("ja_jp", "zh_cn", "zh_tw", "ko_kr", "de_de", "fr_fr",
            "es_es", "it_it", "pt_br", "ru_ru", "pl_pl", "nl_nl", "tr_tr", "uk_ua", "vi_vn", "th_th", "id_id");

    public static List<Entry> entries(Project p) {
        String ns = p.meta.modId;
        List<Entry> r = new ArrayList<>();
        for (Element e : p.elements) {
            switch (e) {
                case Elements.Item it -> r.add(new Entry("item." + ns + "." + it.name, it, "name", or(it.displayName, it.name)));
                case Elements.Block b -> r.add(new Entry("block." + ns + "." + b.name, b, "name", or(b.displayName, b.name)));
                case Elements.CreativeTab t -> r.add(new Entry("itemGroup." + ns + "." + t.name, t, "name", or(t.displayName, t.name)));
                case Elements.Sound s -> {
                    if (!s.subtitle.isBlank()) r.add(new Entry("subtitles." + ns + "." + s.name, s, "subtitle", s.subtitle));
                }
                case Systems.Mob m -> {
                    if (!m.displayName.isBlank() || p.meta.languages.stream().anyMatch(l -> !m.loc("name", l).isBlank()))
                        r.add(new Entry("entity." + ns + "." + m.name, m, "name", or(m.displayName, m.name)));
                }
                case Elements.Advancement a -> {
                    String base = "advancements." + ns + "." + a.name.replace('/', '.');
                    r.add(new Entry(base + ".title", a, "title", or(a.title, a.name)));
                    r.add(new Entry(base + ".description", a, "description", a.description));
                }
                default -> {
                }
            }
        }
        return r;
    }

    static final Map<String, String> SHIFT_HINT = Map.of("en_us", "Hold Shift for details", "ja_jp", "Shift キーで詳細を表示",
            "zh_cn", "按住 Shift 查看详情", "zh_tw", "按住 Shift 查看詳情", "ko_kr", "Shift 키를 눌러 자세히 보기",
            "de_de", "Umschalt halten für Details", "fr_fr", "Maintenez Maj pour plus de détails", "es_es", "Mantén Mayús para ver detalles",
            "pt_br", "Segure Shift para detalhes", "ru_ru", "Удерживайте Shift для подробностей");

    private static String or(String a, String id) {
        return a == null || a.isBlank() ? Ids.titleCase(id) : a;
    }

    public static Set<String> languages(Project p) {
        LinkedHashSet<String> r = new LinkedHashSet<>();
        r.add("en_us");
        r.addAll(p.meta.languages);
        r.addAll(p.translations.keySet());
        return r;
    }

    /** All language files: lang -> key -> text. */
    public static Map<String, Map<String, String>> files(Project p) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        List<Entry> entries = entries(p);
        for (String lang : languages(p)) {
            Map<String, String> m = new LinkedHashMap<>();
            for (Entry e : entries) {
                String v = lang.equals("en_us") ? e.english() : e.element().loc(e.field(), lang);
                if (v != null && !v.isBlank()) m.put(e.key(), v);
            }
            if (p.all(Elements.Item.class).stream().anyMatch(i -> !i.shiftTooltip.isBlank())) {
                String hint = SHIFT_HINT.get(lang);
                if (hint != null) m.put(p.meta.modId + ".tooltip.hold_shift", hint);
            }
            Map<String, String> extra = p.translations.get(lang);
            if (extra != null) extra.forEach((k, v) -> {
                if (!v.isBlank()) m.put(k, v);
            });
            if (!m.isEmpty()) out.put(lang, m);
        }
        return out;
    }
}
