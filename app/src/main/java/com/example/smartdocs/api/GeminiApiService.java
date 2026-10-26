package com.example.smartdocs.api;

import com.example.smartdocs.api.model.GeminiRequest;
import com.example.smartdocs.api.model.GeminiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;
public interface GeminiApiService {

    /*
      Gửi request đến Gemini để sinh văn bản.
      @param apiKey  API Key lấy từ Google AI Studio (truyền qua @Query)
      @param request Body request chứa prompt
      @return Call<GeminiResponse> — Retrofit sẽ tự serialize/deserialize JSON
     */

    @POST("v1beta/models/gemini-3.6-flash:generateContent")
    Call<GeminiResponse> generateContent(
            @Query("key") String apiKey,
            @Body GeminiRequest request
    );
}
