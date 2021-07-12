package android.os;

import java.util.HashMap;
import java.util.Map;

public class Bundle {
    Map<String, Object> map;

    public Bundle() {
        map = new HashMap<>();
    }

    public Object get(String key) {
        return map.get(key);
    }

    public void putString(String key, String value) {
        map.put(key, value);
    }

    public void putDouble(String key, double value) {
        map.put(key, value);
    }

    public void putParcelableArray(String key, Parcelable[] value) {
        map.put(key, value);
    }

    public double getDouble(String key) {
        return (double)map.get(key);
    }

    public String getString(String key) {
        return (String)map.get(key);
    }

    public int size() {
        return map.keySet().size();
    }
}
