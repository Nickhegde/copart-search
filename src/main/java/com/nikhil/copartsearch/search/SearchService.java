package com.nikhil.copartsearch.search;

import com.nikhil.copartsearch.web.dto.SearchResponse;

public interface SearchService {
    SearchResponse search(String query, int page, int size);
}
