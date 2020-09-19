package phbot;

import com.google.api.client.util.Key;

import discord4j.common.util.Snowflake;

public class ChannelInfo {
    @Key
    private String channelName, courseName, appType, groupId, periodId, gcId, roleString;

    private Snowflake role;

    public ChannelInfo() {
    }

    public String getChannelName() {
        return channelName;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getAppType() {
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

    public void build() {
        if (roleString != null)
            role = Snowflake.of(roleString);
    }

    @Override
    public String toString() {
        return channelName;
    }
}
