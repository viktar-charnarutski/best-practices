package model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Books {

    final Map<Integer, String> map = new ConcurrentHashMap<>();

    public int add(String title) {
        final Integer next = map.size() + 1;
        map.put(next, title);
        return next;
    }

    public String title(int id) {
        return map.get(id);
    }
}
