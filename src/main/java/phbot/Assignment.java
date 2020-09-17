package phbot;

import com.google.api.client.util.Key;

public class Assignment {
    @Key
    String title;
    
    public Assignment() {
    }

    public Assignment(String t) {
        title = t;
    }
    
    @Override
    public String toString() {
        return title;
    }
}
