package com.nikhil.copartsearch.model;

public record Lot(int lotNumber, int year, String make, String model,
                  String trim, String titleType, String location, String imageUrl) {}