package org.akvo.akvoqr.choose_striptest;

import org.akvo.akvoqr.util.AssetsManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by linda on 8/19/15.
 */
public class StripTest{

    public static StripTest instance;
    private static JSONArray stripsJson;
    private static Map<String, JSONObject> stripObjects;

    public static StripTest getInstance()
    {
        if(instance==null)
        {
            instance = new StripTest();
            fromJson();
        }
        return instance;
    }

    public Map<String, JSONObject> getStripObjects() {
        return stripObjects;
    }

    public JSONArray getStripsJson() {
        return stripsJson;
    }

    public Set<String> getAllBrands()
    {
        return stripObjects.keySet();
    }

    public List<String> getBrandsAsList()
    {
       List<String> brandnames = new ArrayList<>();
        for(String b: stripObjects.keySet()){
            brandnames.add(b);
        }
       return brandnames;
    }

    public Brand getBrand(String brand)
    {
        return new Brand(brand);
    }

    public class Brand  implements Serializable
    {
        private String name;
        private double stripLenght;
        private double stripHeight;
        private List<Patch> patches = new ArrayList<>();
        private JSONArray instructions;

        public Brand(String brand) {

            if (stripObjects != null) {
                JSONObject strip = stripObjects.get(brand);
                if(strip!=null) {
                    try {
                        this.stripLenght = strip.getDouble("length");
                        this.stripHeight = strip.getDouble("height");
                        this.name = strip.getString("name");
                        this.instructions = strip.getJSONArray("instructions");

                        JSONArray patchesArr = strip.getJSONArray("patches");
                        for(int i=0;i<patchesArr.length();i++) {
                            JSONObject patchObj = patchesArr.getJSONObject(i);
                            String patchDesc = patchObj.getString("patchDesc");
                            int patchPos = patchObj.getInt("patchPos");
                            int patchWidth = patchObj.getInt("patchWidth");
                            double timeLapse = patchObj.getDouble("timeLapse");
                            JSONArray ppmVals = patchObj.getJSONArray("ppmVals");

                            patches.add(new Patch(i, patchDesc, patchWidth, 0, patchPos, timeLapse, ppmVals));
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public List<Patch> getPatches() {
            return patches;
        }

        public double getStripHeight() {
            return stripHeight;
        }

        public double getStripLenght() {
            return stripLenght;
        }

        public String getName() {
            return name;
        }

        public JSONArray getInstructions() {
            return instructions;
        }

        public boolean hasTimeLapse()
        {
            double totalTime = 0;
            for(Patch patch: patches)
            {
                totalTime += patch.timeLapse;
            }

            return totalTime>0;
        }

        public class Patch {
            int order;
            String desc;
            double width; //mm
            double height;//mm
            double position;//x in mm
            double timeLapse; //seconds between this and previous patch
            JSONArray ppmValues;

            public Patch(int order, String desc, double width, double height, double position,
                         double timeLapse, JSONArray ppmValues) {
                this.order = order;
                this.desc = desc;
                this.width = width;
                this.height = height;
                this.position = position;
                this.timeLapse = timeLapse;
                this.ppmValues = ppmValues;
            }

            public String getDesc() {
                return desc;
            }

            public double getPosition() {
                return position;
            }

            public JSONArray getPpmValues() {
                return ppmValues;
            }

            public double getTimeLapse() {
                return timeLapse;
            }
        }
    }
    private static void fromJson()
    {
        String json = AssetsManager.getInstance().loadJSONFromAsset("strips.json");
        try {

            JSONObject object = new JSONObject(json);
            if(!object.isNull("strips"))
            {
                stripsJson = object.getJSONArray("strips");
                if(stripsJson!=null) {
                    stripObjects = new HashMap<>();
                    for (int i = 0; i < stripsJson.length(); i++) {
                        JSONObject strip = stripsJson.getJSONObject(i);
                        String key = strip.getString("brand");
                        stripObjects.put(key, strip);
                    }
                }
            }
            else
            {
                System.out.println("***json object has no strips");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


}