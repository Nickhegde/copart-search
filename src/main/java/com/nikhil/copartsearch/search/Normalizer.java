package com.nikhil.copartsearch.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns free text into search tokens. Indexing and querying must both go through this
 * class so that user input and stored data are normalized identically.
 *
 * <pre>
 *   "Honda CR-V"     -> [honda, crv]
 *   " 2.5i Premium " -> [25i, premium]
 *   "F-150 - XLT"    -> [f150, xlt]
 *   "Citroën"        -> [citroen]
 * </pre>
 *
 * Stateless and thread-safe, so a static utility rather than a Spring bean.
 * Note: the class name shadows java.text.Normalizer, which is used fully qualified below.
 */
public final class Normalizer {

    /** Any Unicode whitespace, including non-breaking spaces from pasted text. */
    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /** Combining marks (accents) left behind after NFD decomposition. */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");

    private Normalizer() {
        // utility class
    }

    /**
     * Lowercases, folds accents, splits on whitespace, strips every character outside
     * a-z0-9 from each token, and drops tokens that end up empty.
     *
     * @return an unmodifiable list; empty for null or blank input
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String lowered = text.toLowerCase(Locale.ROOT);
        String folded = foldAccents(lowered);

        List<String> tokens = new ArrayList<>();
        for (String rawToken : WHITESPACE.split(folded.strip())) {
            String token = NON_ALPHANUMERIC.matcher(rawToken).replaceAll("");
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    /** "ë" decomposes to "e" + a combining diaeresis under NFD; removing the mark leaves "e". */
    private static String foldAccents(String text) {
        String decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("");
    }
}