package com.nikhil.copartsearch.data;

import com.nikhil.copartsearch.model.Lot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class LotRepository {

    private static final Logger log = LoggerFactory.getLogger(LotRepository.class);
    private static final String DATA_FILE = "data/cars.json";

    private final List<Lot> lots;

    public LotRepository(JsonMapper jsonMapper) {
        this.lots = load(jsonMapper);
        log.info("Loaded {} lots from {}", lots.size(), DATA_FILE);
    }

    public List<Lot> findAll() {
        return lots;
    }

    private static List<Lot> load(JsonMapper jsonMapper) {
        ClassPathResource resource = new ClassPathResource(DATA_FILE);
        try (InputStream in = resource.getInputStream()) {
            List<Lot> parsed = jsonMapper.readValue(in, new TypeReference<List<Lot>>() {});
            return List.copyOf(parsed);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + DATA_FILE + " from classpath", e);
        } catch (JacksonException e) {
            throw new IllegalStateException(DATA_FILE + " is not valid lot JSON", e);
        }
    }
}