package net.ai.chatbot.utils;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    public static List<Document> getSplittedDocuments(MultipartFile file) throws IOException, TikaException {
        Tika tika = new Tika();
        String text = tika.parseToString(file.getInputStream());
        Document document = new Document(text);

        TextSplitter splitter = new TokenTextSplitter(true);
        List<Document> smallDocs = splitter.split(document);
        return smallDocs;
    }

    public static List<Document> getSplittedDocuments(String text) throws IOException, TikaException {
        Document document = new Document(text);

        TextSplitter splitter = new TokenTextSplitter(true);
        List<Document> smallDocs = splitter.split(document);
        return smallDocs;
    }

    public static List<Document> getSplittedDocuments(String text, Map<String, Object> metaDataMap) {
        Document document = new Document(text, metaDataMap);
        TextSplitter splitter = new TokenTextSplitter(true);
        List<Document> smallDocs = splitter.split(document);

        return smallDocs;
    }
}
