package phbot;

import com.google.api.client.util.Key;

import discord4j.common.util.Snowflake;

public class ChannelInfo {
    public static final int NONE = 0, GOOGLE_CLASSROOM = 1, SCHOOL_LOOP = 2;

    @Key
    private String channelName, courseName, groupId, periodId, gcId, roleString;

    @Key
    private int appType;

    private Snowflake role;

    public ChannelInfo() {
    }

    public String getChannelName() {
        return channelName;
    }

    public String getCourseName() {
        return courseName;
    }

    public int getAppType() {
        return appType;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getPeriodId() {
        return periodId;
    }

    public String getGcId() {
        return gcId;
    }

    public Snowflake getRole() {
        return role;
    }

    public ChannelInfo build() {
        if (roleString != null)
            role = Snowflake.of(roleString);
        return this;
    }

    @Override
    public String toString() {
        return channelName;
    }
}
