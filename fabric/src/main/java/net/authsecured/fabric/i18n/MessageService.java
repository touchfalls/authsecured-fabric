package net.authsecured.fabric.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.text.Text;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-language (i18n) translation and formatting service for AuthSecured.
 * Supports 5 languages (EN, RU, ES, IT, FR) and parses '&' color codes into Minecraft Text components.
 */
public final class MessageService {

    private static final MessageService INSTANCE = new MessageService();

    private final Map<String, Map<String, String>> translations = new ConcurrentHashMap<>();
    private String defaultLanguage = "en";

    private MessageService() {
        loadLanguage("en");
        loadLanguage("ru");
        loadLanguage("es");
        loadLanguage("it");
        loadLanguage("fr");
    }

    public static MessageService getInstance() {
        return INSTANCE;
    }

    public void loadLanguage(String langCode) {
        String resourcePath = "/lang/" + langCode + ".json";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> map = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), type);
                if (map != null) {
                    translations.put(langCode.toLowerCase(), map);
                }
            }
        } catch (Exception ignored) {}
    }

    public String getRaw(String langCode, String key) {
        Map<String, String> langMap = translations.getOrDefault(langCode.toLowerCase(), translations.get(defaultLanguage));
        if (langMap != null && langMap.containsKey(key)) {
            return langMap.get(key);
        }
        Map<String, String> defaultMap = translations.get(defaultLanguage);
        if (defaultMap != null && defaultMap.containsKey(key)) {
            return defaultMap.get(key);
        }
        return key;
    }

    public Text get(String langCode, String key, Object... args) {
        String raw = getRaw(langCode, key);
        if (args.length > 0) {
            try {
                raw = String.format(raw, args);
            } catch (Exception ignored) {}
        }
        return parseColorCodes(raw);
    }

    public Text get(String key, Object... args) {
        return get(defaultLanguage, key, args);
    }

    public static Text parseColorCodes(String text) {
        if (text == null) return Text.empty();
        return Text.literal(text.replace('&', '§'));
    }
}
