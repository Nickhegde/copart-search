package com.nikhil.copartsearch.search;

import java.util.List;

public interface SuggestService {
    List<String> suggest(String query, int limit);
}
