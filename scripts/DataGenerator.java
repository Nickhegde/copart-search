import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates synthetic vehicle lot data for the car search app.
 *
 * Run from the project root (Java 17+):
 *     java scripts/DataGenerator.java
 *
 * Output: src/main/resources/data/cars.json (1000 lots, identical on every run).
 */
public class DataGenerator {

    private static final long SEED = 42L;
    private static final int LOT_COUNT = 1000;
    private static final int MIN_YEAR = 2012;
    private static final int MAX_YEAR = 2026;
    private static final Path OUTPUT = Path.of("src", "main", "resources", "data", "cars.json");
    private static final String IMAGE_BASE_URL = "https://placehold.co/640x480?text=";

    // ---------------------------------------------------------------------
    // Data types
    // ---------------------------------------------------------------------

    /** A model and the years it was actually sold, so we never emit e.g. a 2014 Kia Telluride. */
    record Model(String name, int firstYear, int lastYear, List<String> trims) {}

    /** A make with a popularity weight; higher weight means more lots in inventory. */
    record Make(String name, int weight, List<Model> models) {}

    record Lot(
            int lotNumber,
            int year,
            String make,
            String model,
            String trim,
            String titleType,
            String location,
            String imageUrl) {}

    // ---------------------------------------------------------------------
    // Catalog
    // ---------------------------------------------------------------------

    /** Model sold for the whole 2012–2026 range. */
    private static Model model(String name, String... trims) {
        return modelBetween(name, MIN_YEAR, MAX_YEAR, trims);
    }

    /** Model sold only within a specific year range (clamped to 2012–2026). */
    private static Model modelBetween(String name, int firstYear, int lastYear, String... trims) {
        int from = Math.max(firstYear, MIN_YEAR);
        int to = Math.min(lastYear, MAX_YEAR);
        return new Model(name, from, to, List.of(trims));
    }

    private static final List<Make> CATALOG = List.of(
            new Make("Toyota", 14, List.of(
                    model("Camry", "LE", "SE", "XLE"),
                    model("Corolla", "L", "LE", "SE"),
                    model("RAV4", "LE", "XLE", "Limited"),
                    model("Tacoma", "SR", "SR5", "TRD Off-Road"),
                    model("Highlander", "LE", "XLE", "Limited"),
                    model("Tundra", "SR", "SR5", "Limited"))),
            new Make("Ford", 13, List.of(
                    model("F-150", "XL", "XLT", "Lariat"),
                    model("Escape", "S", "SE", "Titanium"),
                    model("Explorer", "Base", "XLT", "Limited"),
                    model("Mustang", "EcoBoost", "GT", "GT Premium"),
                    modelBetween("Fusion", 2012, 2020, "S", "SE", "Titanium"),
                    modelBetween("Edge", 2012, 2024, "SE", "SEL", "Titanium"))),
            new Make("Honda", 12, List.of(
                    model("Civic", "LX", "Sport", "EX"),
                    model("Accord", "LX", "Sport", "EX-L"),
                    model("CR-V", "LX", "EX", "EX-L"),
                    model("Pilot", "EX-L", "Touring", "Elite"),
                    model("Odyssey", "EX", "EX-L", "Touring"),
                    modelBetween("HR-V", 2016, 2026, "LX", "Sport", "EX-L"))),
            new Make("Chevrolet", 12, List.of(
                    model("Silverado 1500", "WT", "LT", "LTZ"),
                    modelBetween("Malibu", 2012, 2025, "LS", "LT", "Premier"),
                    model("Equinox", "LS", "LT", "Premier"),
                    model("Tahoe", "LS", "LT", "Premier"),
                    model("Traverse", "LS", "LT", "Premier"),
                    modelBetween("Camaro", 2012, 2024, "1LT", "2LT", "SS"))),
            new Make("Nissan", 10, List.of(
                    model("Altima", "S", "SV", "SL"),
                    model("Rogue", "S", "SV", "SL"),
                    model("Sentra", "S", "SV", "SR"),
                    model("Frontier", "S", "SV", "PRO-4X"),
                    model("Pathfinder", "S", "SV", "Platinum"))),
            new Make("Hyundai", 8, List.of(
                    model("Elantra", "SE", "SEL", "Limited"),
                    model("Sonata", "SE", "SEL", "Limited"),
                    model("Tucson", "SE", "SEL", "Limited"),
                    model("Santa Fe", "SE", "SEL", "Limited"),
                    modelBetween("Kona", 2018, 2026, "SE", "SEL", "Limited"))),
            new Make("Kia", 7, List.of(
                    modelBetween("Optima", 2012, 2020, "LX", "EX", "SX"),
                    modelBetween("K5", 2021, 2026, "LXS", "GT-Line", "EX"),
                    model("Sorento", "LX", "EX", "SX"),
                    model("Sportage", "LX", "EX", "SX"),
                    model("Soul", "LX", "EX", "GT-Line"),
                    modelBetween("Telluride", 2020, 2026, "LX", "EX", "SX"))),
            new Make("Jeep", 7, List.of(
                    model("Wrangler", "Sport", "Sahara", "Rubicon"),
                    model("Grand Cherokee", "Laredo", "Limited", "Overland"),
                    modelBetween("Cherokee", 2014, 2023, "Latitude", "Limited", "Trailhawk"),
                    model("Compass", "Sport", "Latitude", "Limited"))),
            new Make("Dodge", 5, List.of(
                    modelBetween("Charger", 2012, 2023, "SXT", "GT", "R/T"),
                    modelBetween("Challenger", 2012, 2023, "SXT", "GT", "R/T"),
                    model("Durango", "SXT", "GT", "Citadel"),
                    modelBetween("Grand Caravan", 2012, 2020, "SE", "SXT", "Crew"))),
            new Make("Ram", 5, List.of(
                    model("1500", "Tradesman", "Big Horn", "Laramie"),
                    model("2500", "Tradesman", "Big Horn", "Laramie"),
                    model("3500", "Tradesman", "Big Horn", "Laramie"),
                    modelBetween("ProMaster", 2014, 2026, "1500 Low Roof", "2500 High Roof", "3500 High Roof"))),
            new Make("Subaru", 5, List.of(
                    model("Outback", "2.5i Premium", "2.5i Limited", "3.6R Limited"),
                    model("Forester", "2.5i", "2.5i Premium", "2.5i Limited"),
                    model("Impreza", "2.0i", "2.0i Premium", "Sport"),
                    modelBetween("Crosstrek", 2013, 2026, "2.0i Premium", "2.0i Limited", "Sport"),
                    model("Legacy", "2.5i Premium", "2.5i Limited", "Sport"),
                    modelBetween("Ascent", 2019, 2026, "Premium", "Limited", "Touring"))),
            new Make("GMC", 4, List.of(
                    model("Sierra 1500", "SLE", "SLT", "Denali"),
                    model("Acadia", "SLE", "SLT", "Denali"),
                    model("Terrain", "SLE", "SLT", "Denali"),
                    model("Yukon", "SLE", "SLT", "Denali"))),
            new Make("BMW", 4, List.of(
                    model("3 Series", "330i", "330i xDrive", "M340i"),
                    model("5 Series", "530i", "540i xDrive", "M550i"),
                    model("X3", "sDrive30i", "xDrive30i", "M40i"),
                    model("X5", "sDrive40i", "xDrive40i", "M50i"))),
            new Make("Mercedes-Benz", 3, List.of(
                    model("C-Class", "C 300", "C 300 4MATIC", "AMG C 43"),
                    model("E-Class", "E 350", "E 450 4MATIC", "AMG E 53"),
                    modelBetween("GLC", 2016, 2026, "GLC 300", "GLC 300 4MATIC", "AMG GLC 43"),
                    modelBetween("GLE", 2016, 2026, "GLE 350", "GLE 450 4MATIC", "AMG GLE 53"))),
            new Make("Tesla", 3, List.of(
                    model("Model S", "Long Range", "Plaid"),
                    modelBetween("Model 3", 2017, 2026, "Standard Range", "Long Range", "Performance"),
                    modelBetween("Model X", 2016, 2026, "Long Range", "Plaid"),
                    modelBetween("Model Y", 2020, 2026, "Long Range", "Performance")))
    );

    private static final List<String> YARDS = List.of(
            "TX - DALLAS SOUTH",
            "TX - HOUSTON",
            "TX - SAN ANTONIO",
            "CA - LOS ANGELES",
            "CA - SACRAMENTO",
            "FL - ORLANDO NORTH",
            "FL - MIAMI CENTRAL",
            "GA - ATLANTA EAST",
            "IL - SOUTHERN ILLINOIS",
            "IL - CHICAGO NORTH",
            "AZ - PHOENIX",
            "NY - LONG ISLAND",
            "NJ - TRENTON",
            "OH - COLUMBUS",
            "NC - RALEIGH"
    );

    // ---------------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        Random random = new Random(SEED);
        List<Lot> lots = generateLots(random);
        writeJson(lots, OUTPUT);
        printSummary(lots);
    }

    // ---------------------------------------------------------------------
    // Generation
    // ---------------------------------------------------------------------

    private static List<Lot> generateLots(Random random) {
        Set<Integer> usedLotNumbers = new HashSet<>();
        List<Lot> lots = new ArrayList<>(LOT_COUNT);
        for (int i = 0; i < LOT_COUNT; i++) {
            lots.add(generateLot(random, usedLotNumbers));
        }
        return lots;
    }

    private static Lot generateLot(Random random, Set<Integer> usedLotNumbers) {
        Make make = pickMake(random);
        Model model = pickFrom(make.models(), random);
        String trim = pickFrom(model.trims(), random);
        int year = randomBetween(model.firstYear(), model.lastYear(), random);

        return new Lot(
                nextLotNumber(random, usedLotNumbers),
                year,
                make.name(),
                model.name(),
                trim,
                pickTitleType(random),
                pickFrom(YARDS, random),
                imageUrl(make.name(), model.name()));
    }

    /** Weighted pick: a make with weight 14 is chosen ~4.7x as often as one with weight 3. */
    private static Make pickMake(Random random) {
        int totalWeight = 0;
        for (Make make : CATALOG) {
            totalWeight += make.weight();
        }

        int roll = random.nextInt(totalWeight);
        for (Make make : CATALOG) {
            roll -= make.weight();
            if (roll < 0) {
                return make;
            }
        }
        throw new IllegalStateException("Weighted pick fell through; weights must be positive");
    }

    /** Clean ~60%, Salvage ~35%, Non-Repairable ~5%. */
    private static String pickTitleType(Random random) {
        int roll = random.nextInt(100);
        if (roll < 60) {
            return "Clean";
        }
        if (roll < 95) {
            return "Salvage";
        }
        return "Non-Repairable";
    }

    /** Unique 8-digit number in [10,000,000, 99,999,999]. */
    private static int nextLotNumber(Random random, Set<Integer> usedLotNumbers) {
        int candidate;
        do {
            candidate = 10_000_000 + random.nextInt(90_000_000);
        } while (!usedLotNumbers.add(candidate));
        return candidate;
    }

    /** Generic placeholder image labelled with make and model; repeats across lots by design. */
    private static String imageUrl(String make, String model) {
        String label = make + " " + model;
        return IMAGE_BASE_URL + URLEncoder.encode(label, StandardCharsets.UTF_8);
    }

    private static <T> T pickFrom(List<T> items, Random random) {
        return items.get(random.nextInt(items.size()));
    }

    private static int randomBetween(int minInclusive, int maxInclusive, Random random) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    // ---------------------------------------------------------------------
    // JSON output (hand-written, no libraries)
    // ---------------------------------------------------------------------

    private static void writeJson(List<Lot> lots, Path output) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        for (int i = 0; i < lots.size(); i++) {
            json.append("  ").append(toJson(lots.get(i)));
            if (i < lots.size() - 1) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("]\n");

        Files.createDirectories(output.getParent());
        Files.writeString(output, json.toString(), StandardCharsets.UTF_8);
    }

    private static String toJson(Lot lot) {
        List<String> fields = List.of(
                numberField("lotNumber", lot.lotNumber()),
                numberField("year", lot.year()),
                stringField("make", lot.make()),
                stringField("model", lot.model()),
                stringField("trim", lot.trim()),
                stringField("titleType", lot.titleType()),
                stringField("location", lot.location()),
                stringField("imageUrl", lot.imageUrl()));
        return "{" + String.join(", ", fields) + "}";
    }

    private static String numberField(String name, int value) {
        return quote(name) + ": " + value;
    }

    private static String stringField(String name, String value) {
        return quote(name) + ": " + quote(value);
    }

    private static String quote(String value) {
        return "\"" + escape(value) + "\"";
    }

    /** Escapes per RFC 8259: quote, backslash, and all control characters below U+0020. */
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    // ---------------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------------

    private static void printSummary(List<Lot> lots) {
        Map<String, Integer> countPerMake = new LinkedHashMap<>();
        for (Make make : CATALOG) {
            countPerMake.put(make.name(), 0);
        }
        for (Lot lot : lots) {
            countPerMake.merge(lot.make(), 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(countPerMake.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        System.out.println("Wrote " + lots.size() + " lots to " + OUTPUT.toAbsolutePath());
        System.out.println();
        System.out.println("Lots per make:");
        for (Map.Entry<String, Integer> entry : sorted) {
            System.out.printf("  %-15s %4d%n", entry.getKey(), entry.getValue());
        }
    }
}
