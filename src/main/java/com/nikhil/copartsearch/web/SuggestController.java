package com.nikhil.copartsearch.web;

import com.nikhil.copartsearch.search.SuggestService;
import com.nikhil.copartsearch.web.dto.SuggestResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SuggestController {

    private final SuggestService suggestService;

    public SuggestController(SuggestService suggestService) {
        this.suggestService = suggestService;
    }

    @GetMapping("/suggest")
    public SuggestResponse suggest(@RequestParam(defaultValue = "") String q,
                                   @RequestParam(defaultValue = "10") int limit) {
        return new SuggestResponse(suggestService.suggest(q, limit));
    }
}