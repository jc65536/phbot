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

    public Assignment() {
    }

    public String getTitle() {
        return title;
    }

    public Calendar getDueDate() {
        return dueDate;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDueDate(Calendar dueDate) {
        this.dueDate = dueDate;
    }

    public void build() throws ParseException {
        dueDate = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        dueDate.setTime(format.parse(dueDateString));
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
