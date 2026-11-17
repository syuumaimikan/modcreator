package modstudio.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Base class of every project element (item, block, recipe, ...). */
public abstract class Element {
    public String uid = Ids.uid();
    public String name = "";
    /** Free notes about the element for the project's authors; shown in lists, never exported. */
    public String notes = "";
    /** Localized texts: field -> (game language code -> text), e.g. loc["name"]["ja_jp"]. */
    public Map<String, Map<String, String>> loc = new LinkedHashMap<>();

    public abstract ElementType type();

    public String loc(String field, String lang) {
        Map<String, String> m = loc.get(field);
        return m == null ? "" : m.getOrDefault(lang, "");
    }

    public void setLoc(String field, String lang, String value) {
        if (value == null || value.isBlank()) {
            Map<String, String> m = loc.get(field);
            if (m != null) {
                m.remove(lang);
                if (m.isEmpty()) loc.remove(field);
            }
            return;
        }
        loc.computeIfAbsent(field, k -> new LinkedHashMap<>()).put(lang, value);
    }

    /** Secondary text shown in element lists. */
    public String subtitle() {
        return "";
    }
}
