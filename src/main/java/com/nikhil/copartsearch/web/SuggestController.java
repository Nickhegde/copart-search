package com.nikhil.copartsearch.web;

import com.nikhil.copartsearch.web.dto.SuggestResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SuggestController {
    @GetMapping("/suggest")
    public SuggestResponse suggest(@RequestParam(defaultValue = "") String q,
                                   @RequestParam(defaultValue = "10") int limit) {
        return new SuggestResponse(List.of());
    }
}
