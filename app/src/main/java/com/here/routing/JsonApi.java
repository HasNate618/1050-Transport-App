package com.here.routing;

import android.content.Context;
import android.os.Debug;
import android.util.Log;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.json.JSONObject;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonApi {
    private static final String JSON_BIN_URL = "https://api.jsonbin.io/v3/b/67e592e08a456b79667de663";
    private static final String API_KEY = "YOUR_API_KEY"; // (if you use a private bin)

    public interface DataCallback {
        void onSuccess(List<PointOfInterest> points);
        void onError(String error);
    }

    public static void fetchJsonData(Context context, DataCallback callback) {
        RequestQueue queue = Volley.newRequestQueue(context);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, JSON_BIN_URL, null,
                response -> {
                    try {
                        // Extract "points" array
                        JSONObject record = response.getJSONObject("record");
                        String jsonArray = record.getJSONArray("points").toString();

                        // Parse JSON using Gson
                        Gson gson = new GsonBuilder()
                                .registerTypeAdapter(PointOfInterest.class, new PointOfInterestDeserializer())
                                .create();
                        Type listType = new TypeToken<ArrayList<PointOfInterest>>() {}.getType();
                        List<PointOfInterest> points = gson.fromJson(jsonArray, listType);

                        callback.onSuccess(points);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                },
                error -> callback.onError(error.toString())
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("X-Master-Key", Secrets.X_MASTER_KEY); // Needed if your bin is private
                return headers;
            }
        };

        queue.add(request);
    }
}
