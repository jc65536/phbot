package phbot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.google.api.client.util.Key;

public class Assignment implements Comparable<Assignment> {
    public static final Calendar NO_DUE_DATE = Calendar.getInstance();
    
    static {
        NO_DUE_DATE.set(Calendar.YEAR, 9999);
    }

    @Key("title")
    String title;

    @Key("endDay")
    String dueDateString;

    String id;
    Calendar dueDate;
    String url;
    String details;
    double points;

    public Assignment() {
    }

    public Assignment(String id, String title, Calendar dueDate, String url, String details, Double points) {
        this.id = id;
        this.title = title;
        this.dueDate = dueDate;
        this.url = url;
        this.details = details;
        if (points != null)
            this.points = points;
        else
            this.points = 0;
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

    public String getDetails() {
        return details;
    }

    public double getPoints() {
        return points;
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

    public void setDetails(String details) {
        this.details = details;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public Assignment build() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        try {
            dueDate = Calendar.getInstance();
            dueDate.setTime(format.parse(dueDateString));
        } catch (ParseException e) {
            System.out.println("[WARNING] Could not parse this Assignment's dueDateString; defaulting to NO_DUE_DATE");
            dueDate = NO_DUE_DATE;
        }
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

    public int compareTo(Calendar c) {
        return dueDate.compareTo(c);
    }
}
