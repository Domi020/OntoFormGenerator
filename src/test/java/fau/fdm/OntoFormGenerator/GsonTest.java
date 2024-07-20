package fau.fdm.OntoFormGenerator;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Map;


public class GsonTest {

    @Test
    public void testGsonParse() {

        var json = "{\n" +
                "\"ontologyName\": \"restaurantOnt_5c883fe5-d8fa-4dc4-8b17-be8a26908ed0\",\n" +
                "\"targetClass\": \"Order\",\n" +
                "\"instanceName\": \"kh\",\n" +
                "\"hasID\": \"9\"\n" +
                "}";

        var gson = new Gson();
        var draftMap = gson.fromJson(json, Map.class);
        System.out.println("test");
    }
}
