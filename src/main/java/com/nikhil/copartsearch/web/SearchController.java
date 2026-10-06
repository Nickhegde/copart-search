package com.nikhil.copartsearch.web;

import com.nikhil.copartsearch.web.dto.SearchResponse;
import com.nikhil.copartsearch.search.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam(defaultValue = "") String q,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        RequestValidation.requireMaxLength("q", q, RequestValidation.MAX_QUERY_LENGTH);
        RequestValidation.requireAtLeast("page", page, 1);
        RequestValidation.requireBetween("size", size, 1, RequestValidation.MAX_PAGE_SIZE);

        return searchService.search(q, page, size);
    }
}