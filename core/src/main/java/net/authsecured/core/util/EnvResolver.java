package net.authsecured.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for resolving environment variable placeholders in format ${ENV:VAR_NAME}.
 */
public final class EnvResolver {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{ENV:([A-Za-z0-9_]+)\\}");

    private EnvResolver() {}

    /**
     * Resolves all ${ENV:VAR_NAME} placeholders in the input string.
     *
     * @param input Raw configuration string.
     * @return Resolved string with environment variable values.
     */
    public static String resolve(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        Matcher matcher = ENV_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String envName = matcher.group(1);
            String envValue = System.getenv(envName);
            if (envValue == null) {
                envValue = "";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
