package com.nikhil.copartsearch.search;

import com.nikhil.copartsearch.data.LotRepository;
import com.nikhil.copartsearch.model.Lot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typeahead suggestions over "Make Model" and "Model Trim" phrases, ranked by how many
 * lots each phrase covers. Built once at startup; read-only afterwards.
 */
@Service
public class InMemorySuggestService implements SuggestService {

    private record Phrase(String text, int lotCount, List<String> tokens) {}

    private final List<Phrase> phrasesByRank;               // phrase id = index; best first
    private final Map<String, List<Integer>> prefixIndex;   // prefix -> phrase ids, in rank order

    public InMemorySuggestService(LotRepository repository) {
        this.phrasesByRank = buildRankedPhrases(repository.findAll());
        this.prefixIndex = buildPrefixIndex(phrasesByRank);
    }

    @Override
    public List<String> suggest(String query, int limit) {
        List<String> queryTokens = Normalizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<Integer> candidates = smallestPostingList(queryTokens);

        // Candidates are already in rank order, so the first `limit` matches are the best ones.
        List<String> results = new ArrayList<>(limit);
        for (int id : candidates) {
            Phrase phrase = phrasesByRank.get(id);
            if (matchesAllTokens(phrase, queryTokens)) {
                results.add(phrase.text());
                if (results.size() == limit) {
                    break;
                }
            }
        }
        return results;
    }

    // ---------------------------------------------------------------- building

    private static List<Phrase> buildRankedPhrases(List<Lot> lots) {
        Map<String, Integer> lotCountByPhrase = new HashMap<>();
        for (Lot lot : lots) {
            lotCountByPhrase.merge(lot.make() + " " + lot.model(), 1, Integer::sum);
            lotCountByPhrase.merge(lot.model() + " " + lot.trim(), 1, Integer::sum);
        }

        List<Phrase> phrases = new ArrayList<>(lotCountByPhrase.size());
        for (Map.Entry<String, Integer> entry : lotCountByPhrase.entrySet()) {
            String text = entry.getKey();
            phrases.add(new Phrase(text, entry.getValue(), Normalizer.tokenize(text)));
        }

        phrases.sort(Comparator.comparingInt(Phrase::lotCount).reversed()
                .thenComparing(Phrase::text));
        return List.copyOf(phrases);
    }

    private static Map<String, List<Integer>> buildPrefixIndex(List<Phrase> phrasesByRank) {
        Map<String, List<Integer>> index = new HashMap<>();

        // Iterating ids in rank order means every posting list ends up sorted by rank.
        for (int id = 0; id < phrasesByRank.size(); id++) {
            // A Set, so a phrase like "Premium Plus" (both tokens start with "p")
            // is added under "p" once, not twice. Otherwise it would show up twice in results.
            Set<String> prefixes = new HashSet<>();
            for (String token : phrasesByRank.get(id).tokens()) {
                for (int length = 1; length <= token.length(); length++) {
                    prefixes.add(token.substring(0, length));
                }
            }
            for (String prefix : prefixes) {
                index.computeIfAbsent(prefix, k -> new ArrayList<>()).add(id);
            }
        }
        return index;
    }

    // ---------------------------------------------------------------- querying

    /** The shortest candidate list among the query tokens, or empty if any token matches nothing. */
    private List<Integer> smallestPostingList(List<String> queryTokens) {
        List<Integer> smallest = null;
        for (String token : queryTokens) {
            List<Integer> ids = prefixIndex.get(token);
            if (ids == null) {
                return List.of();
            }
            if (smallest == null || ids.size() < smallest.size()) {
                smallest = ids;
            }
        }
        return smallest;
    }

    /** AND semantics: every query token must be a prefix of some token in the phrase. */
    private static boolean matchesAllTokens(Phrase phrase, List<String> queryTokens) {
        for (String queryToken : queryTokens) {
            boolean found = false;
            for (String phraseToken : phrase.tokens()) {
                if (phraseToken.startsWith(queryToken)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}