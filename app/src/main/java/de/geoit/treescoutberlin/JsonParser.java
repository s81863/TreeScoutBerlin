package de.geoit.treescoutberlin;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class JsonParser {

    public static List<FeatureInfo> parseJsonFile(Context context, String filename) {
        String json = JsonUtils.loadJSONFromAsset(context, filename);

        if (json == null) {
            return new ArrayList<>();
        }

        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

        if (jsonObject != null && jsonObject.has("features")) {
            JsonArray featuresArray = jsonObject.getAsJsonArray("features");

            List<FeatureInfo> featureList = new ArrayList<>();

            for (int i = 0; i < featuresArray.size(); i++) {
                JsonObject featureObject = featuresArray.get(i).getAsJsonObject();

                if (featureObject != null && featureObject.has("properties")) {
                    JsonObject propertiesObject = featureObject.getAsJsonObject("properties");
                    String planungsraum = propertiesObject.has("planungsraum") ? propertiesObject.get("planungsraum").getAsString() : "";
                    String bezirksname = propertiesObject.has("bezirksname") ? propertiesObject.get("bezirksname").getAsString() : "";
                    String bezirksregion = propertiesObject.has("bezirksregion") ? propertiesObject.get("bezirksregion").getAsString() : "";
                    String prognoseraum = propertiesObject.has("prognoseraum") ? propertiesObject.get("prognoseraum").getAsString() : "";

                    if (featureObject.has("geometry")) {
                        JsonObject geometryObject = featureObject.getAsJsonObject("geometry");
                        if (geometryObject.get("type").getAsString().equals("Polygon")) {
                            JsonArray coordinatesArray = geometryObject.getAsJsonArray("coordinates");
                            List<List<List<Double>>> coordinatesList = gson.fromJson(
                                    coordinatesArray,
                                    new ArrayList<Double>().getClass()
                            );
                            FeatureInfo featureInfo = new FeatureInfo(planungsraum, bezirksname, bezirksregion, prognoseraum, coordinatesList);
                            featureList.add(featureInfo);
                        } else if (geometryObject.get("type").getAsString().equals("Point")) {
                            JsonArray coordinatesArray = geometryObject.getAsJsonArray("coordinates");
                            List<Double> coordinatesList = gson.fromJson(
                                    coordinatesArray,
                                    new ArrayList<Double>().getClass()
                            );
                            FeatureInfo featureInfo = new FeatureInfo(planungsraum, bezirksname, bezirksregion, planungsraum, coordinatesList);
                            featureList.add(featureInfo);
                        }
                    }
                }
            }

            return featureList;
        }

        return new ArrayList<>();
    }
}
