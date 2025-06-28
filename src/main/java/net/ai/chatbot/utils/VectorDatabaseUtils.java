package net.ai.chatbot.utils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class VectorDatabaseUtils {

    public static String getNameSpace(String email, String... keywords) {

        if (keywords == null) {
            throw new RuntimeException("Please dont pass empty suffix namespace");
        }

        return email
                .concat(":")
                .concat(Arrays.stream(keywords).collect(Collectors.joining(":")));
    }

}
