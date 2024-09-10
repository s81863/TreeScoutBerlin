package de.geoit.treescoutberlin;

import android.content.Context;
import java.io.IOException;
import java.io.InputStream;

public class JsonUtils {
    public static String loadJSONFromAsset(Context context, String filename) {
        String json = null;
        try {
            InputStream inputStream = context.getAssets().open(filename);

            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            json = new String(buffer, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return json;
    }
}