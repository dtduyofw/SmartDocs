package com.example.smartdocs.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;
public class GeminiRequest {

    @SerializedName("contents")
    public List<Content> contents;

    @SerializedName("generationConfig")
    public GenerationConfig generationConfig;

    //Tạo request với một prompt text.
    public GeminiRequest(String promptText) {
        Part part = new Part(promptText);
        Content content = new Content(Collections.singletonList(part));
        this.contents = Collections.singletonList(content);
        this.generationConfig = new GenerationConfig(0.7f, 8192);
    }

    // Inner classes ánh xạ cấu trúc JSON lồng nhau
    public static class Content {
        @SerializedName("parts")
        public List<Part> parts;

        public Content(List<Part> parts) {
            this.parts = parts;
        }
    }

    public static class Part {
        @SerializedName("text")
        public String text;

        public Part(String text) {
            this.text = text;
        }
    }

    public static class GenerationConfig {
        @SerializedName("temperature")
        public float temperature;

        @SerializedName("maxOutputTokens")
        public int maxOutputTokens;

        public GenerationConfig(float temperature, int maxOutputTokens) {
            this.temperature = temperature;
            this.maxOutputTokens = maxOutputTokens;
        }
    }
}
