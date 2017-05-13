package st.alr.homA.model;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import st.alr.homA.support.Defaults;
import android.content.Context;
import android.preference.PreferenceManager;

public class Quickpublish {
    private String name = Defaults.VALUE_QUICKPUBLISH_NAME;
    private String topic = "";
    private String payload = "";
    private boolean retained = false;
    
    public Quickpublish(String name, String topic, String payload, boolean retained) {
        this.name = name != null && !name.equals("") ? name : Defaults.VALUE_QUICKPUBLISH_NAME;
        this.topic = topic;
        this.payload = payload; 
        this.retained = retained;
    }

    public Quickpublish(JSONObject jsonObject) {
        try {
            this.topic = jsonObject.getString("t");
            this.payload = jsonObject.getString("p");
            this.retained = jsonObject.getString("r").equals("1");
            this.name = jsonObject.has("n")? jsonObject.getString("n") : Defaults.VALUE_QUICKPUBLISH_NAME;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPayload() {
        return payload;
    }
    public void setPayload(String payload) {
        this.payload = payload;
    }

    public boolean isRetained() {
        return retained;
    }

    public void setRetained(boolean retained) {
        this.retained = retained;
    }
    
    public String toJsonString() {
        return toJsonString(true);
    }

    public String toJsonString(boolean includeName) {
        JSONObject object = new JSONObject();
        try {
          object.put("t", this.topic);
          object.put("p", this.payload);
          object.put("r", this.retained ? "1" : "0" );
          if(includeName && name != null && !this.name.equals(Defaults.VALUE_QUICKPUBLISH_NAME))
              object.put("n", this.name);
        } catch (JSONException e) {
          e.printStackTrace();
        }
        return object.toString();
    }

    public String toString() {
        return toJsonString();
    }

    public static String toJsonString(ArrayList<Quickpublish> list) {
        StringBuilder b = new StringBuilder();
        b.append("[");
        
        if (list.size() > 0) {
            b.append(list.get(0).toJsonString());
        }
        
        for (int i=1; i<list.size(); i++) {
            b.append(",");
            b.append(list.get(i).toJsonString());
        }
        b.append("]");

        return b.toString();
    }

    public static ArrayList<Quickpublish> fromJsonString(String json) {
        ArrayList<Quickpublish> list = new ArrayList<>();
        
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++)
                list.add(i, new Quickpublish(a.getJSONObject(i)));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return list;
    }
    
    public static ArrayList<Quickpublish> fromPreferences(Context context, String key) {
        return Quickpublish.fromJsonString(
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(key, Defaults.VALUE_QUICKPUBLISH_JSON));
    }

    public static void toPreferences(Context context, String key, ArrayList<Quickpublish> values) {
       PreferenceManager.getDefaultSharedPreferences(context).edit()
               .putString(key, Quickpublish.toJsonString(values)).apply();
    }
}
