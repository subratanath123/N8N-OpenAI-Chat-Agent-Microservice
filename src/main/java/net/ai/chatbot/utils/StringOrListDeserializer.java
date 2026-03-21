package net.ai.chatbot.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Accepts either a JSON array ["a","b"] or a comma-separated string "a, b"
 * and produces a List<String>.
 */
public class StringOrListDeserializer extends StdDeserializer<List<String>> {

    public StringOrListDeserializer() {
        super(List.class);
    }

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        if (p.currentToken() == JsonToken.START_ARRAY) {
            List<String> list = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                list.add(p.getText());
            }
            return list;
        }
        // String value — split on comma
        String raw = p.getText();
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
