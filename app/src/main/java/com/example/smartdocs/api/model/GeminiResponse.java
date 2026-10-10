package com.example.smartdocs.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
public class GeminiResponse {

    @SerializedName("candidates")
    public List<Candidate> candidates;

    /*
      Phương thức tiện ích — trích xuất nội dung text từ response.
      Trả về null nếu response rỗng hoặc cấu trúc không hợp lệ.
     */
    public String extractText() {
        if (candidates == null || candidates.isEmpty()) return null;

        Candidate firstCandidate = candidates.get(0);
        if (firstCandidate.content == null) return null;
        if (firstCandidate.content.parts == null
                || firstCandidate.content.parts.isEmpty()) return null;

        return firstCandidate.content.parts.get(0).text;
    }

    // Inner classes ánh xạ cấu trúc JSON lồng nhau
    public static class Candidate {
        @SerializedName("content")
        public Content content;
    }

    public static class Content {
        @SerializedName("parts")
        public List<Part> parts;
    }

    public static class Part {
        @SerializedName("text")
        public String text;
    }
}
