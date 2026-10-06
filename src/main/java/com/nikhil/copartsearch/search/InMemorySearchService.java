package com.nikhil.copartsearch.search;

import com.nikhil.copartsearch.data.LotRepository;
import com.nikhil.copartsearch.model.Lot;
import com.nikhil.copartsearch.web.dto.SearchResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search over an in-memory inverted prefix index, built once at startup.
 * The index is never modified after construction, so concurrent reads are safe.
 */
@Service
public class InMemorySearchService implements SearchService {

    /** Default order for blank queries and tie-breaks: newest first, then lot number. */
    private static final Comparator<Lot> DEFAULT_ORDER =
            Comparator.comparingInt(Lot::year).reversed()
                    .thenComparingInt(Lot::lotNumber);

    /** A matched lot plus its relevance score, so scores are computed once per query. */
    private record ScoredLot(Lot lot, int score) {}

    private final List<Lot> lots;                          // lot id = index in this list
    private final Map<String, Set<Integer>> prefixIndex;   // prefix -> lot ids
    private final List<Set<String>> makeModelTokensById;   // for exact-match ranking
    private final List<Lot> defaultOrder;                  // precomputed for blank queries

    public InMemorySearchService(LotRepository repository) {
        this.lots = repository.findAll();
        this.prefixIndex = new HashMap<>();
        this.makeModelTokensById = new ArrayList<>(lots.size());

        for (int id = 0; id < lots.size(); id++) {
            Lot lot = lots.get(id);
            indexLot(lot, id);
            makeModelTokensById.add(Set.copyOf(Normalizer.tokenize(lot.make() + " " + lot.model())));
        }

        List<Lot> sorted = new ArrayList<>(lots);
        sorted.sort(DEFAULT_ORDER);
        this.defaultOrder = List.copyOf(sorted);
    }

    @Override
    public SearchResponse search(String query, int page, int size) {
        List<String> queryTokens = Normalizer.tokenize(query);
        List<Lot> ranked = queryTokens.isEmpty() ? defaultOrder : findAndRank(queryTokens);
        return paginate(query, ranked, page, size);
    }

    // ---------------------------------------------------------------- indexing

    private void indexLot(Lot lot, int id) {
        String text = lot.year() + " " + lot.make() + " " + lot.model() + " " + lot.trim();
        for (String token : Normalizer.tokenize(text)) {
            for (int length = 1; length <= token.length(); length++) {
                addToIndex(token.substring(0, length), id);
            }
        }
        // Lot number is indexed as the full value only: users search lot numbers exactly,
        // and prefixes would make short numeric queries like "20" match almost everything.
        addToIndex(String.valueOf(lot.lotNumber()), id);
    }

    private void addToIndex(String key, int id) {
        prefixIndex.computeIfAbsent(key, k -> new HashSet<>()).add(id);
    }

    // ---------------------------------------------------------------- querying

    private List<Lot> findAndRank(List<String> queryTokens) {
        Set<Integer> matchingIds = intersect(queryTokens);

        List<ScoredLot> scored = new ArrayList<>(matchingIds.size());
        for (int id : matchingIds) {
            scored.add(new ScoredLot(lots.get(id), exactMatchScore(id, queryTokens)));
        }

        scored.sort(Comparator.comparingInt(ScoredLot::score).reversed()
                .thenComparing(ScoredLot::lot, DEFAULT_ORDER));

        List<Lot> ranked = new ArrayList<>(scored.size());
        for (ScoredLot s : scored) {
            ranked.add(s.lot());
        }
        return ranked;
    }

    /** AND semantics: a lot must match every query token. */
    private Set<Integer> intersect(List<String> queryTokens) {
        List<Set<Integer>> postings = new ArrayList<>(queryTokens.size());
        for (String token : queryTokens) {
            Set<Integer> ids = prefixIndex.get(token);
            if (ids == null) {
                return Set.of();   // one token matches nothing, so the AND result is empty
            }
            postings.add(ids);
        }

        // Start from the smallest set: the result can never be larger than it.
        postings.sort(Comparator.comparingInt(Set::size));

        Set<Integer> result = new HashSet<>(postings.get(0));   // copy; never mutate the index
        for (int i = 1; i < postings.size() && !result.isEmpty(); i++) {
            result.retainAll(postings.get(i));
        }
        return result;
    }

    /** Number of query tokens that exactly equal a make or model token ("honda" beats "hon"). */
    private int exactMatchScore(int id, List<String> queryTokens) {
        Set<String> makeModelTokens = makeModelTokensById.get(id);
        int score = 0;
        for (String token : queryTokens) {
            if (makeModelTokens.contains(token)) {
                score++;
            }
        }
        return score;
    }

    // ---------------------------------------------------------------- pagination

    private static SearchResponse paginate(String query, List<Lot> ranked, int page, int size) {
        int total = ranked.size();
        int totalPages = (total + size - 1) / size;              // ceiling division
        long from = (long) (page - 1) * size;                    // long: huge page numbers can't overflow

        List<Lot> items;
        if (from >= total) {
            items = List.of();                                    // past the last page: empty, not an error
        } else {
            int to = (int) Math.min(from + size, total);
            items = List.copyOf(ranked.subList((int) from, to));
        }
        return new SearchResponse(query, page, size, total, totalPages, items);
    }
}