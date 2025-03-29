package com.here.routing;

import android.content.Context;
import android.os.Debug;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JsonApi {
    private static final String JSON_BIN_URL = "https://api.jsonbin.io/v3/b/67e592e08a456b79667de663";

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

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static void removePOIInBackground(String titleToRemove) {
        executorService.execute(() -> {
            try {
                removePOI(titleToRemove);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void removePOI(String titleToRemove) {
        try {
            // 1. Fetch current POI data from the JSON bin
            URL url = new URL(JSON_BIN_URL);  // Replace with your JSON Bin URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-Master-Key", Secrets.X_MASTER_KEY);  // Replace with your API key

            connection.connect();

            // Read the response
            InputStream inStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            connection.disconnect();

            // Parse the fetched JSON
            JSONObject jsonResponse = new JSONObject(responseBuilder.toString());
            JSONArray pointsArray = jsonResponse.getJSONObject("record").getJSONArray("points");

            // 2. Remove POI from the array based on the passed title
            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject poi = pointsArray.getJSONObject(i);
                String title = poi.getString("title");

                if (title.equals(titleToRemove)) {
                    pointsArray.remove(i);
                    break;
                }
            }

            // 3. Prepare the updated JSON to send back to the JSON bin, without the "record" wrapper
            JSONObject updatedJson = new JSONObject();
            updatedJson.put("points", pointsArray); // Only include the "points" array

            // 4. Send the updated data back with a PUT request
            URL putUrl = new URL(JSON_BIN_URL);  // Replace with your JSON Bin URL
            HttpURLConnection putConnection = (HttpURLConnection) putUrl.openConnection();
            putConnection.setRequestMethod("PUT");
            putConnection.setRequestProperty("X-Master-Key", Secrets.X_MASTER_KEY);
            putConnection.setRequestProperty("Content-Type", "application/json");

            putConnection.setDoOutput(true);
            try (OutputStream os = putConnection.getOutputStream()) {
                byte[] input = updatedJson.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Get the response from the PUT request
            int responseCode = putConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("POI removed and updated successfully.");
            } else {
                System.out.println("Failed to update POIs. Response Code: " + responseCode);
            }

            putConnection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error occurred while removing POI: " + e.getMessage());
        }
    }
}
