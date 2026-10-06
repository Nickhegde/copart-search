package com.nikhil.copartsearch.web.dto;

import com.nikhil.copartsearch.model.Lot;

import java.util.List;

public record SearchResponse(String query, int page, int size,
                             long totalItems, int totalPages, List<Lot> items) {}
