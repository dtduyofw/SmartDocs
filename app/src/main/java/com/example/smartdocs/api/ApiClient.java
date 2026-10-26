package com.example.smartdocs.api;

import com.example.smartdocs.BuildConfig;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

public class ApiClient {

    // Base URL của Gemini REST API
    static final String BASE_URL = "https://generativelanguage.googleapis.com/";

    // Timeout: 30 giây
    static final int TIMEOUT_SECONDS = 60;

    // Singleton instance
    static ApiClient instance;
    GeminiApiService apiService;

    private ApiClient() {
        // Cấu hình OkHttp
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        // Debug: log full request/response để debug; Release: NONE để không lộ API key
        loggingInterceptor.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        //Cấu hình Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())// Gson tự động parse JSON ↔ Java object
                .build();

        //Tạo implementation của interface
        apiService = retrofit.create(GeminiApiService.class);
    }

    //Trả về singleton ApiClient .
    public static ApiClient getInstance() {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = new ApiClient();
                }
            }
        }
        return instance;
    }

    //Trả về service để thực hiện API call.
    public GeminiApiService getApiService() {
        return apiService;
    }
}
