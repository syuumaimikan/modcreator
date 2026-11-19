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
        Systems.KeyBind categoryOwner = null;
        for (Element e : p.elements) {
            switch (e) {
                case Elements.Item it -> r.add(new Entry("item." + ns + "." + it.name, it, "name", or(it.displayName, it.name)));
                case Elements.Block b -> r.add(new Entry("block." + ns + "." + b.name, b, "name", or(b.displayName, b.name)));
                case Elements.Fluid fl -> {
                    String n = or(fl.displayName, fl.name);
                    r.add(new Entry("block." + ns + "." + fl.name, fl, "name", n));
                    if (fl.generateBucket) r.add(new Entry("item." + ns + "." + fl.name + "_bucket", fl, "bucket", n + " Bucket"));
                }
                case Elements.CreativeTab t -> r.add(new Entry("itemGroup." + ns + "." + t.name, t, "name", or(t.displayName, t.name)));
                case Elements.Sound s -> {
                    if (!s.subtitle.isBlank()) r.add(new Entry("subtitles." + ns + "." + s.name, s, "subtitle", s.subtitle));
                }
                case Systems.Mob m -> {
                    if (!m.displayName.isBlank() || p.meta.languages.stream().anyMatch(l -> !m.loc("name", l).isBlank()))
                        r.add(new Entry("entity." + ns + "." + m.name, m, "name", or(m.displayName, m.name)));
                }
                case Elements.StatusEffect se -> {
                    String n = or(se.displayName, se.name);
                    r.add(new Entry("effect." + ns + "." + se.name, se, "name", n));
                    if (se.potion) {
                        String pk = ns + "." + se.name;
                        String pn = se.potionName.isBlank() ? "Potion of " + n : se.potionName;
                        r.add(new Entry("item.minecraft.potion.effect." + pk, se, "potion", pn));
                        r.add(new Entry("item.minecraft.splash_potion.effect." + pk, se, "potion", "Splash " + pn));
                        r.add(new Entry("item.minecraft.lingering_potion.effect." + pk, se, "potion", "Lingering " + pn));
                        r.add(new Entry("item.minecraft.tipped_arrow.effect." + pk, se, "potion", "Arrow of " + n));
                    }
                }
                case Elements.Potion po -> {
                    String pk = ns + "." + po.name;
                    String n = or(po.displayName, po.name);
                    r.add(new Entry("item.minecraft.potion.effect." + pk, po, "name", n));
                    r.add(new Entry("item.minecraft.splash_potion.effect." + pk, po, "splash", po.splashName.isBlank() ? "Splash " + n : po.splashName));
                    r.add(new Entry("item.minecraft.lingering_potion.effect." + pk, po, "lingering",
                            po.lingeringName.isBlank() ? "Lingering " + n : po.lingeringName));
                    r.add(new Entry("item.minecraft.tipped_arrow.effect." + pk, po, "arrow", po.arrowName.isBlank() ? "Tipped Arrow (" + n + ")" : po.arrowName));
                }
                case Elements.Enchantment en -> r.add(new Entry("enchantment." + ns + "." + en.name, en, "name", or(en.displayName, en.name)));
                case Elements.DamageType d -> {
                    String death = d.deathMessage.isBlank() ? "%1$s died" : d.deathMessage;
                    r.add(new Entry("death.attack." + ns + "." + d.name, d, "death", death));
                    r.add(new Entry("death.attack." + ns + "." + d.name + ".player", d, "death_attacker",
                            d.deathMessageAttacker.isBlank() ? death + " whilst fighting %2$s" : d.deathMessageAttacker));
                }
                case Elements.Painting pa -> {
                    r.add(new Entry("painting." + ns + "." + pa.name + ".title", pa, "title", or(pa.title, pa.name)));
                    r.add(new Entry("painting." + ns + "." + pa.name + ".author", pa, "author", pa.author));
                }
                case Elements.Song so -> r.add(new Entry("jukebox_song." + ns + "." + so.name, so, "description", or(so.description, so.name)));
                case Systems.KeyBind k -> {
                    r.add(new Entry("key." + ns + "." + k.name, k, "name", or(k.displayName, k.name)));
                    if ((k.category.equals("mod") || k.category.isBlank()) && categoryOwner == null) categoryOwner = k;
                }
                case Elements.Advancement a -> {
                    String base = "advancements." + ns + "." + a.name.replace('/', '.');
                    r.add(new Entry(base + ".title", a, "title", or(a.title, a.name)));
                    r.add(new Entry(base + ".description", a, "description", a.description));
                }
                case Systems.Dialog d -> {
                    String base = "dialog." + ns + "." + d.name;
                    if (!d.title.isBlank() || hasLoc(p, d, "title")) r.add(new Entry(base + ".title", d, "title", or(d.title, d.name)));
                    if (!d.body.isBlank() || hasLoc(p, d, "body")) r.add(new Entry(base + ".body", d, "body", d.body));
                }
                case Systems.Menu mn -> {
                    String base = "menu." + ns + "." + mn.name;
                    if (!mn.title.isBlank() || hasLoc(p, mn, "title")) r.add(new Entry(base + ".title", mn, "title", or(mn.title, mn.name)));
                    if (!mn.denyMessage.isBlank() || hasLoc(p, mn, "deny")) r.add(new Entry(base + ".deny", mn, "deny", mn.denyMessage));
                }
                case Systems.Hud h -> {
                    if (!h.title.isBlank() || hasLoc(p, h, "title")) r.add(new Entry("hud." + ns + "." + h.name + ".title", h, "title", or(h.title, h.name)));
                    for (int i = 0; i < h.lines.size(); i++) {
                        String line = h.lines.get(i);
                        String f = "line." + i;
                        if ((line != null && !line.isBlank()) || hasLoc(p, h, f)) {
                            r.add(new Entry("hud." + ns + "." + h.name + "." + f, h, f, line == null ? "" : line));
                        }
                    }
                }
                case Systems.CustomScreen sc -> {
                    if (!sc.title.isBlank() || hasLoc(p, sc, "title")) r.add(new Entry("screen." + ns + "." + sc.name + ".title", sc, "title", or(sc.title, sc.name)));
                    for (int i = 0; i < sc.widgets.size(); i++) {
                        Systems.ScreenWidget w = sc.widgets.get(i);
                        String pf = "w." + i;
                        String kpf = "screen." + ns + "." + sc.name + "." + pf;
                        if ((w.text != null && !w.text.isBlank()) || hasLoc(p, sc, pf + ".text")) {
                            r.add(new Entry(kpf + ".text", sc, pf + ".text", w.text == null ? "" : w.text));
                        }
                        if (!sc.overlay() && ((w.tooltip != null && !w.tooltip.isBlank()) || hasLoc(p, sc, pf + ".tooltip"))) {
                            r.add(new Entry(kpf + ".tooltip", sc, pf + ".tooltip", w.tooltip == null ? "" : w.tooltip));
                        }
                        if ("textfield".equals(w.kind) && ((w.placeholder != null && !w.placeholder.isBlank()) || hasLoc(p, sc, pf + ".placeholder"))) {
                            r.add(new Entry(kpf + ".placeholder", sc, pf + ".placeholder", w.placeholder == null ? "" : w.placeholder));
                        }
                        if ("scroll_list".equals(w.kind) && ((w.options != null && !w.options.isBlank()) || hasLoc(p, sc, pf + ".options"))) {
                            r.add(new Entry(kpf + ".options", sc, pf + ".options", w.options == null ? "" : w.options));
                        }
                    }
                }
                case Systems.Trader tr -> {
                    if (!tr.displayName.isBlank() || hasLoc(p, tr, "name")) r.add(new Entry("trader." + ns + "." + tr.name, tr, "name", or(tr.displayName, tr.name)));
                }
                case Systems.Resource res -> {
                    if (!res.displayName.isBlank() || hasLoc(p, res, "name")) r.add(new Entry("resource." + ns + "." + res.name, res, "name", or(res.displayName, res.name)));
                }
                case Systems.Biome bi -> r.add(new Entry("biome." + ns + "." + bi.name, bi, "name", Ids.titleCase(bi.name)));
                case Systems.Dimension dim -> r.add(new Entry("dimension." + ns + "." + dim.name, dim, "name", Ids.titleCase(dim.name)));
                case ModElements.Container ct -> r.add(new Entry("container." + ns + "." + ct.name, ct, "name", or(ct.displayName, ct.name)));
                case ModElements.CustomEntity ce -> {
                    String n = or(ce.displayName, ce.name);
                    r.add(new Entry("entity." + ns + "." + ce.name, ce, "name", n));
                    if (ce.spawnEgg && ce.isLivingMob())
                        r.add(new Entry("item." + ns + "." + ce.name + "_spawn_egg", ce, "spawn_egg", n + " Spawn Egg"));
                }
                case ModElements.Portal pt -> {
                    String n = or(pt.displayName, pt.name);
                    r.add(new Entry("block." + ns + "." + pt.name + "_portal", pt, "name", n + " Portal"));
                    if (pt.generateIgniter)
                        r.add(new Entry("item." + ns + "." + pt.name + "_igniter", pt, "igniter", n + " Igniter"));
                }
                case ModElements.Profession pf -> r.add(new Entry("entity.minecraft.villager." + ns + "." + pf.name, pf, "name", or(pf.displayName, pf.name)));
                case ModElements.ArmorTrim at -> {
                    String n = or(at.displayName, at.name);
                    r.add(new Entry("trim_pattern." + ns + "." + at.name, at, "name", n + " Armor Trim"));
                    if (at.generateTemplate)
                        r.add(new Entry("item." + ns + "." + at.name + "_armor_trim_smithing_template", at, "template", "Smithing Template"));
                }
                case ModElements.BannerPattern bp -> r.add(new Entry("block.minecraft.banner." + ns + "." + bp.name, bp, "name", or(bp.displayName, bp.name)));
                default -> {
                }
            }
            if (e instanceof Elements.Item it) {
                if (!it.tooltip.isBlank() || hasLoc(p, it, "tooltip")) r.add(new Entry("item." + ns + "." + it.name + ".tooltip", it, "tooltip", it.tooltip));
                if (!it.shiftTooltip.isBlank() || hasLoc(p, it, "shift_tooltip"))
                    r.add(new Entry("item." + ns + "." + it.name + ".shift_tooltip", it, "shift_tooltip", it.shiftTooltip));
            }
        }
        if (categoryOwner != null) {
            r.add(new Entry("key.categories." + ns, categoryOwner, "category", p.meta.name.isBlank() ? ns : p.meta.name));
        }
        return r;
    }

    private static boolean hasLoc(Project p, Element e, String field) {
        return p.meta.languages.stream().anyMatch(l -> !e.loc(field, l).isBlank());
    }

    static final Map<String, String> SHIFT_HINT = Map.ofEntries(
            Map.entry("en_us", "Hold Shift for details"),
            Map.entry("ja_jp", "Shift キーで詳細を表示"),
            Map.entry("zh_cn", "按住 Shift 查看详情"),
            Map.entry("zh_tw", "按住 Shift 查看詳情"),
            Map.entry("ko_kr", "Shift 키를 눌러 자세히 보기"),
            Map.entry("de_de", "Umschalt halten für Details"),
            Map.entry("fr_fr", "Maintenez Maj pour plus de détails"),
            Map.entry("es_es", "Mantén Mayús para ver detalles"),
            Map.entry("it_it", "Tieni premuto Maiusc per i dettagli"),
            Map.entry("pt_br", "Segure Shift para detalhes"),
            Map.entry("ru_ru", "Удерживайте Shift для подробностей"),
            Map.entry("pl_pl", "Przytrzymaj Shift, aby zobaczyć szczegóły"),
            Map.entry("nl_nl", "Houd Shift ingedrukt voor details"),
            Map.entry("tr_tr", "Ayrıntılar için Shift tuşunu basılı tutun"),
            Map.entry("uk_ua", "Утримуйте Shift для деталей"),
            Map.entry("vi_vn", "Giữ Shift để xem chi tiết"),
            Map.entry("th_th", "กด Shift ค้างไว้เพื่อดูรายละเอียด"),
            Map.entry("id_id", "Tahan Shift untuk detail"));

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
                if (v != null && !v.isBlank()) {
                    if (e.element() instanceof Systems.Hud) v = formatHudText(e.english(), v);
                    m.put(e.key(), v);
                }
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

    /** Unique `{token}` names in order of appearance within an English HUD template. */
    public static List<String> hudTokens(String text) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return List.of();
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf('{', i);
            if (open < 0) break;
            int close = text.indexOf('}', open + 1);
            if (close < 0) break;
            String tok = text.substring(open + 1, close);
            if (!tok.isEmpty() && !tok.contains("{")) tokens.add(tok);
            i = close + 1;
        }
        return List.copyOf(tokens);
    }

    /** Formats a HUD string (base `&` color codes and `{token}` -> `%N$s` ordered after englishSource). */
    public static String formatHudText(String englishSource, String targetText) {
        if (targetText == null || targetText.isEmpty()) return "";
        List<String> order = hudTokens(englishSource);
        String colored = targetText.replaceAll("&([0-9a-fA-Fk-oK-OrRxX])", "§$1");
        if (order.isEmpty()) return colored;
        StringBuilder sb = new StringBuilder(colored.length());
        int i = 0;
        while (i < colored.length()) {
            char c = colored.charAt(i);
            if (c == '%') {
                // Keep valid positional specs written directly by translators (%1$s, %10$s, %1$.1f); escape stray '%'
                int posEnd = matchPositionalFormat(colored, i);
                if (posEnd > i) {
                    sb.append(colored, i, posEnd);
                    i = posEnd;
                    continue;
                }
                sb.append("%%");
                i++;
                continue;
            }
            if (c == '{') {
                int close = colored.indexOf('}', i + 1);
                if (close > i + 1) {
                    String tok = colored.substring(i + 1, close);
                    int idx = order.indexOf(tok);
                    if (idx >= 0) {
                        sb.append('%').append(idx + 1).append("$s");
                        i = close + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int matchPositionalFormat(String s, int pctIdx) {
        int j = pctIdx + 1;
        int n = s.length();
        if (j >= n) return -1;
        while (j < n && Character.isDigit(s.charAt(j))) j++;
        if (j == pctIdx + 1 || j >= n || s.charAt(j) != '$') return -1;
        j++;
        while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.' || s.charAt(j) == '-' || s.charAt(j) == '+')) j++;
        if (j < n && "sdfoxegcDFXEG".indexOf(s.charAt(j)) >= 0) return j + 1;
        return -1;
    }
}
