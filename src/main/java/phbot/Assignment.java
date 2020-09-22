package phbot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.google.api.client.util.Key;

public class Assignment implements Comparable<Assignment> {
    @Key("title")
    String title;

    @Key("endDay")
    String dueDateString;
    
    Calendar dueDate;
    String url;

    public Assignment() {
    }

    public Assignment(String title, Calendar dueDate, String url) {
        this.title = title;
        this.dueDate = dueDate;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public Calendar getDueDate() {
        return dueDate;
    }
    
    public String getUrl() {
        return url;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDueDate(Calendar dueDate) {
        this.dueDate = dueDate;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Assignment build() throws ParseException {
        dueDate = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        dueDate.setTime(format.parse(dueDateString));
        return this;
    }
    
    @Override
    public String toString() {
        return title;
    }

    @Override
    public int compareTo(Assignment a) {
        return dueDate.compareTo(a.getDueDate());
    }
}
