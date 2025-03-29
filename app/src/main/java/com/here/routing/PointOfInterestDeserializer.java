package com.here.routing;

import com.google.gson.*;
import com.here.sdk.core.GeoCoordinates;

import java.lang.reflect.Type;

public class PointOfInterestDeserializer implements JsonDeserializer<PointOfInterest> {
    @Override
    public PointOfInterest deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();

        String type = obj.get("type").getAsString();
        String title = obj.get("title").getAsString();
        String description = obj.get("description").getAsString();
        GeoCoordinates coordinates = context.deserialize(obj.get("coordinates"), GeoCoordinates.class);
        boolean userSubmitted = obj.get("userSubmitted").getAsBoolean();

        return new PointOfInterest(type, title, description, coordinates, userSubmitted);
    }
}
