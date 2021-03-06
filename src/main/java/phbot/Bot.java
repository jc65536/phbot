package phbot;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.classroom.Classroom;
import com.google.api.services.classroom.model.ListCourseWorkResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;

interface Command {
    void execute(MessageCreateEvent event);
}

public class Bot {
    static final Snowflake GUILD_ID = Snowflake.of("617861584005496859");
    private static final String APPLICATION_NAME = "phbot";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    static NetHttpTransport HTTP_TRANSPORT;
    private static Map<String, Command> commands = new HashMap<>();
    private static Map<Snowflake, ChannelInfo> channelMapper;
    static Classroom service;
    static GatewayDiscordClient client;

    static HttpRequestInitializer getGoogleCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        try (FileInputStream fin = new FileInputStream("oauth_tokens.json")) {
            UserCredentials credentials = UserCredentials.fromStream(fin);
            credentials.refreshIfExpired();
            return new HttpCredentialsAdapter(credentials);
        } catch (IOException e) {
            System.out.println("[ERROR] Missing tokens file or could not refresh tokens");
            e.printStackTrace();
            throw e;
        }
    }

    static String readDiscordSecret() throws IOException {
        try (BufferedReader fin = new BufferedReader(new FileReader("discord_secret.txt"))) {
            String s = fin.readLine();
            return s;
        } catch (FileNotFoundException e1) {
            System.out.println("[ERROR] Discord secret file missing");
            throw e1;
        } catch (IOException e2) {
            System.out.println("[ERROR] IOException while reading Discord secret");
            throw e2;
        }
    }

    static HttpResponse sendRequest(HttpRequest request) throws IOException {
        System.out.println("\n========== Request ==========");
        System.out.println(request.getRequestMethod() + " " + request.getUrl().build());

        request.getHeaders().forEach((s, o) -> {
            System.out.printf("%s: %s\n", s, o.toString());
        });
        if (request.getRequestMethod().equals("POST")) {
            System.out.println("\nBody:");
            try {
                request.getContent().writeTo(System.out);
                System.out.println();
            } catch (IOException e) {
                System.out.println("[ERROR] IOException while printing request body");
            }
        }

        try {
            HttpResponse response = request.execute();

            System.out.println("\n========== Response ==========");
            System.out.println(response.getStatusCode());
            response.getHeaders().forEach((s, o) -> {
                System.out.printf("%s: %s\n", s, o.toString());
            });

            return response;
        } catch (IOException e) {
            System.out.println("[ERROR] IOException while executing request");
            throw e;
        }

    }

    static List<Assignment> getAssignments(ChannelInfo channel) throws IOException {
        List<Assignment> assignments = new ArrayList<>();
        switch (channel.getAppType()) {
            case ChannelInfo.SCHOOL_LOOP:
                HttpRequestFactory factory = HTTP_TRANSPORT.createRequestFactory();
                HttpRequest request;
                HttpResponse response;

                try {
                    request = factory.buildGetRequest(new GenericUrl("https://phhs.schoolloop.com/portal/login"));
                    response = request.execute();
                } catch (IOException e) {
                    System.out.println("[ERROR] Initial GET request to School Loop failed");
                    throw e;
                }

                String cookies = response.getHeaders().get("set-cookie").toString();
                Matcher matcher = Pattern.compile("JSESSIONID=[^;]+").matcher(cookies);
                matcher.find();
                String jsi = matcher.group();

                String html;
                try {
                    html = response.parseAsString();
                } catch (IOException e) {
                    System.out.println("[ERROR] Could not read School Loop's form_data_id");
                    throw e;
                }
                matcher = Pattern.compile("form_data_id\" value=\"([^\"]+)").matcher(html);
                matcher.find();
                String fdi = matcher.group(1);

                Map<String, String> params = new HashMap<>();
                try (BufferedReader reader = new BufferedReader(new FileReader("sl_password.txt"))) {
                    params.put("login_name", reader.readLine());
                    params.put("password", reader.readLine());
                    params.put("form_data_id", fdi);
                    params.put("event_override", "login");
                } catch (FileNotFoundException e1) {
                    System.out.println("[ERROR] School Loop password file missing");
                    throw e1;
                } catch (IOException e2) {
                    System.out.println("[ERROR] Failed to read School Loop password");
                    throw e2;
                }
                HttpContent postData = new UrlEncodedContent(params);

                try {
                    request = factory.buildPostRequest(
                            new GenericUrl("https://phhs.schoolloop.com/portal/login?etarget=login_form"), postData);
                    request.getHeaders().setCookie(jsi).setContentType("application/x-www-form-urlencoded");
                    request.setFollowRedirects(false).setThrowExceptionOnExecuteError(false).execute();
                } catch (IOException e) {
                    System.out.println("[ERROR] School Loop login POST request failed");
                    throw e;
                }

                Calendar cal = Calendar.getInstance();
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                String startDate = formatter.format(cal.getTime());
                cal.add(Calendar.DATE, 3);
                String endDate = formatter.format(cal.getTime());
                String urlString = String.format(
                        "https://phhs.schoolloop.com/pf4/cal/eventsInRange?group_id=%s&period_id=%s&start_date=%s&end_date=%s",
                        channel.getGroupId(), channel.getPeriodId(), startDate, endDate);

                try {
                    request = factory.buildGetRequest(new GenericUrl(urlString));
                    request.getHeaders().setCookie(jsi);
                    response = request.execute();
                } catch (IOException e) {
                    System.out.println("[ERROR] Failed to get assignment data from School Loop");
                    throw e;
                }

                try (JsonParser parser = JSON_FACTORY.createJsonParser(response.getContent())) {
                    parser.parseArray(assignments, Assignment.class);
                    assignments.forEach(a -> {
                        a.setTitle(a.getTitle().replaceAll("\\s", " "));
                        a.build();
                    });
                } catch (IOException e) {
                    System.out.println("[ERROR] Failed to parse School Loop data into Assignment objects");
                    throw e;
                }
                break;

            case ChannelInfo.GOOGLE_CLASSROOM:
                ListCourseWorkResponse courseWorkResponse;
                try {
                    courseWorkResponse = service.courses().courseWork().list(channel.getGcId()).setPageSize(5)
                            .execute();
                } catch (IOException e) {
                    System.out.println("[ERROR] Failed to get assignment data from Google Classroom");
                    throw e;
                }
                assignments = courseWorkResponse.getCourseWork().stream().map(w -> {
                    com.google.api.services.classroom.model.Date googleDate = w.getDueDate();
                    if (googleDate == null)
                        return new Assignment(w.getId(), w.getTitle(), Assignment.NO_DUE_DATE, w.getAlternateLink(),
                                w.getDescription(), w.getMaxPoints());
                    Calendar dueDate = Calendar.getInstance();
                    dueDate.set(googleDate.getYear(), googleDate.getMonth() - 1, googleDate.getDay());
                    dueDate.clear(Calendar.HOUR);
                    dueDate.clear(Calendar.MINUTE);
                    dueDate.clear(Calendar.SECOND);
                    dueDate.clear(Calendar.MILLISECOND);
                    return new Assignment(w.getId(), w.getTitle(), dueDate, w.getAlternateLink(), w.getDescription(),
                            w.getMaxPoints());
                }).filter(a -> a.compareTo(Calendar.getInstance()) >= 0).collect(Collectors.toList());
                break;
        }
        Collections.sort(assignments);
        return assignments;
    }

    static int calcLevDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 1; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
        }
        return d[a.length()][b.length()];
    }

    static Pattern optionPattern(String optionName, String shorthand) {
        return Pattern.compile(
                String.format("(?:-%s|--%s)\\s*+\\\"?((?<=\\\")[^\\\"]+(?=\\\")|\\S+)", shorthand, optionName));
    }

    public static void main(String[] args) {
        // Read info associated with each channel from channels.json
        JsonParser channelInfoParser;
        Map<?, ?> temp;
        try {
            channelInfoParser = JSON_FACTORY.createJsonParser(new FileInputStream("channels.json"));
            temp = channelInfoParser.parse(Map.class);
            channelMapper = temp.entrySet().stream()
                    .collect(Collectors.toMap(e -> Snowflake.of(e.getKey().toString()), e -> {
                        try {
                            return JSON_FACTORY.fromString(JSON_FACTORY.toString(e.getValue()), ChannelInfo.class)
                                    .build();
                        } catch (IOException ex) {
                            System.out.println(
                                    "[ERROR] Failed to parse this ChannelInfo object: " + e.getValue().toString());
                            return new ChannelInfo();
                        }
                    }));
        } catch (FileNotFoundException e1) {
            System.out.println("[ERROR] channels.json missing");
            System.exit(1);
        } catch (IOException e2) {
            System.out.println("[ERROR] Failed to read channels.json into channelMapper");
            System.exit(1);
        }

        // Start Google Classroom client
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("[ERROR] Could not create secure HTTP transport");
            System.exit(1);
        }

        try {
            service = new Classroom.Builder(HTTP_TRANSPORT, JSON_FACTORY, getGoogleCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME).build();
        } catch (IOException e) {
            System.exit(1);
        }

        // Start Discord client
        try {
            client = DiscordClientBuilder.create(readDiscordSecret()).build().login().block();
        } catch (IOException e) {
            System.exit(1);
        }

        // Define commands
        commands.put("p", event -> event.getMessage().getChannel()
                .flatMap(channel -> channel.createMessage(channel.getId().asString())).subscribe());

        commands.put("cw", event -> {
            String command = event.getMessage().getContent();
            MessageChannel channel = event.getMessage().getChannel().block();
            ChannelInfo info = channelMapper.get(channel.getId());

            List<Assignment> assignments;
            try {
                assignments = getAssignments(info);
            } catch (IOException e) {
                channel.createMessage("Error(s) occurred while getting assignment data for this class.").subscribe();
                return;
            }

            Matcher optionMatcher = optionPattern("details", "d").matcher(command);
            if (optionMatcher.find()) {
                command = command.substring(0, optionMatcher.start()) + command.substring(optionMatcher.end());
                String query = optionMatcher.group(1).trim().toLowerCase().replaceAll("\\W", "");
                String title = "Search results - \"" + query + "\"";
                int minLevDistance = 1000;
                Assignment closest = new Assignment();
                // TODO: Make better matching algorithm
                for (Assignment a : assignments) {
                    String t = a.getTitle().toLowerCase().replaceAll("\\W", "");
                    if (t.contains(query)) {
                        closest = a;
                        break;
                    }
                    if (calcLevDistance(query, t) < minLevDistance) {
                        closest = a;
                        minLevDistance = calcLevDistance(query, t);
                    }
                }

                // Fuck Java lambdas >:(
                Assignment copy = closest;
                channel.createEmbed(embed -> {
                    embed.setTitle(title);
                    if (info.getAppType() == ChannelInfo.GOOGLE_CLASSROOM) {
                        embed.setDescription(String.format("[%s](%s)", copy.getTitle(), copy.getUrl()));
                    } else {
                        embed.setDescription(copy.getTitle());
                    }
                    if (copy.getPoints() > 0) {
                        embed.addField("Points", Double.toString(copy.getPoints()), true);
                    } else {
                        embed.addField("Points", "None", true);
                    }
                    SimpleDateFormat formatter = new SimpleDateFormat("MM/dd");
                    if (copy.getDueDate() != Assignment.NO_DUE_DATE) {
                        embed.addField("Due", formatter.format(copy.getDueDate().getTime()), true);
                    } else {
                        embed.addField("Due", "No due date", true);
                    }
                    if (copy.getDetails() != null) {
                        embed.addField("Details", copy.getDetails(), false);
                    }
                    embed.setColor(client.getRoleById(GUILD_ID, info.getRole()).map(r -> r.getColor()).block());
                }).subscribe();
            } else {
                if (info.getAppType() == ChannelInfo.NONE) {
                    channel.createMessage("This is not a class channel.").subscribe();
                    return;
                }
                String courseName;
                courseName = info.getCourseName();
                String title = "Upcoming assignments for " + courseName;
                channel.createMessage(spec -> spec.setEmbed(embed -> {
                    embed.setTitle(title);
                    if (assignments.size() > 0) {
                        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd");
                        Calendar lastDue = assignments.get(0).getDueDate();
                        StringBuilder assignmentStringBuilder = new StringBuilder();
                        if (info.getAppType() == ChannelInfo.GOOGLE_CLASSROOM) {
                            for (Assignment a : assignments) {
                                if (a.compareTo(lastDue) > 0) {
                                    embed.addField("Due " + formatter.format(lastDue.getTime()),
                                            assignmentStringBuilder.toString(), false);
                                    assignmentStringBuilder.setLength(0);
                                    lastDue = a.getDueDate();
                                }
                                assignmentStringBuilder
                                        .append(String.format("\u2611 [%s](%s)\n", a.getTitle(), a.getUrl()));
                            }
                        } else {
                            for (Assignment a : assignments) {
                                if (a.compareTo(lastDue) > 0) {
                                    embed.addField("Due " + formatter.format(lastDue.getTime()),
                                            assignmentStringBuilder.toString(), false);
                                    assignmentStringBuilder.setLength(0);
                                    lastDue = a.getDueDate();
                                }
                                assignmentStringBuilder.append("\u2611 " + a.getTitle() + "\n");
                            }
                        }
                        if (lastDue != Assignment.NO_DUE_DATE) {
                            embed.addField("Due " + formatter.format(lastDue.getTime()),
                                    assignmentStringBuilder.toString(), false);
                        } else {
                            embed.addField("No due date", assignmentStringBuilder.toString(), false);
                        }
                    } else {
                        embed.setDescription("No upcoming assignments! :D");
                    }
                    embed.setColor(client.getRoleById(GUILD_ID, info.getRole()).map(r -> r.getColor()).block());
                })).subscribe();
            }
        });

        commands.put("remindme", event -> {
            MessageChannel channel = event.getMessage().getChannel().block();
            String command = event.getMessage().getContent();

            String remindMessage;
            long repeat = 0;
            long delay = 0;
            String error = null;

            Matcher optionMatcher = Pattern.compile("(?:-m|--message)\\s*+\\\"?((?<=\\\")[^\\\"]+(?=\\\")|\\S+)")
                    .matcher(command);
            if (optionMatcher.find()) {
                command = command.substring(0, optionMatcher.start()) + command.substring(optionMatcher.end());
                remindMessage = optionMatcher.group(1);
            } else {
                remindMessage = "Reminder!";
            }

            optionMatcher = Pattern.compile("(?:-o|--on)\\s*+\\\"?((?<=\\\")[^\\\"]+(?=\\\")|\\S+)").matcher(command);
            if (optionMatcher.find()) {
                command = command.substring(0, optionMatcher.start()) + command.substring(optionMatcher.end());
                String dateString = optionMatcher.group(1);
                SimpleDateFormat formatter = new SimpleDateFormat("MM/dd HH:mm");
                Calendar remindDate = Calendar.getInstance();
                Calendar now = Calendar.getInstance();
                try {
                    remindDate.setTime(formatter.parse(dateString));
                    remindDate.set(Calendar.YEAR, now.get(Calendar.YEAR));
                    if (remindDate.compareTo(now) < 0)
                        remindDate.add(Calendar.YEAR, 1);
                    delay = (remindDate.getTime().getTime() - now.getTime().getTime()) / 1000 / 60;
                } catch (ParseException e) {
                    error = "Reminder time was not specified.";
                }
            } else {
                optionMatcher = Pattern.compile("(?:-i|--in)\\s*+\\\"?((?<=\\\")[^\\\"]+(?=\\\")|\\S+)")
                        .matcher(command);
                if (optionMatcher.find()) {
                    command = command.substring(0, optionMatcher.start()) + command.substring(optionMatcher.end());
                    String timeString = optionMatcher.group(1);
                    optionMatcher = Pattern.compile(
                            "\\s*(?:(\\d+)\\s*(?:days|d))?\\s*(?:(\\d+)\\s*(?:hours|h))?\\s*(?:(\\d+)\\s*(?:minutes|m))?\\s*")
                            .matcher(timeString);
                    optionMatcher.find();
                    long d = 0;
                    if (optionMatcher.group(1) != null)
                        d += 24 * 60 * Integer.parseInt(optionMatcher.group(1));
                    if (optionMatcher.group(2) != null)
                        d += 60 * Integer.parseInt(optionMatcher.group(2));
                    if (optionMatcher.group(3) != null)
                        d += Integer.parseInt(optionMatcher.group(3));
                    delay = d;
                } else {
                    error = "Reminder time was not specified.";
                }
            }

            optionMatcher = optionPattern("repeat", "r").matcher(command);
            if (optionMatcher.find()) {
                String repeatValue = optionMatcher.group(1);
                switch (repeatValue) {
                    case "daily":
                        repeat = 24 * 60;
                        break;
                    case "weekly":
                        repeat = 7 * 24 * 60;
                        break;
                    default:
                        optionMatcher = Pattern.compile(
                                "\\s*(?:(\\d+)\\s*(?:days|d))?\\s*(?:(\\d+)\\s*(?:hours|h))?\\s*(?:(\\d+)\\s*(?:minutes|m))?\\s*")
                                .matcher(repeatValue);
                        optionMatcher.find();
                        long d = 0;
                        if (optionMatcher.group(1) != null)
                            d += 24 * 60 * Integer.parseInt(optionMatcher.group(1));
                        if (optionMatcher.group(2) != null)
                            d += 60 * Integer.parseInt(optionMatcher.group(2));
                        if (optionMatcher.group(3) != null)
                            d += Integer.parseInt(optionMatcher.group(3));
                        repeat = d;
                }
            }

            if (error == null) {
                TimerTask reminder = new TimerTask() {
                    @Override
                    public void run() {
                        channel.createMessage(remindMessage).subscribe();
                    }
                };
                System.out.printf("Reminder created:\n  delay = %d\n  repeat = %d\n  message = %s\n", delay, repeat,
                        remindMessage);
                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                if (repeat == 0) {
                    executor.schedule(reminder, delay, TimeUnit.MINUTES);
                } else {
                    executor.scheduleAtFixedRate(reminder, delay, repeat, TimeUnit.MINUTES);
                }
            } else {
                channel.createMessage(error).subscribe();
            }
        });

        commands.put("help", event -> {

        });

        // Set event listeners for login and new message
        client.getEventDispatcher().on(ReadyEvent.class).subscribe(event -> {
            User self = event.getSelf();
            System.out.println(String.format("Logged in as %s#%s", self.getUsername(), self.getDiscriminator()));
        });

        client.getEventDispatcher().on(MessageCreateEvent.class).subscribe(event -> {
            event.
            final String content = event.getMessage().getContent();
            for (final Map.Entry<String, Command> entry : commands.entrySet()) {

                if (content.startsWith('!' + entry.getKey())) {
                    System.out.println(entry.getKey() + " command");
                    entry.getValue().execute(event);
                    break;
                }
            }
        });

        client.onDisconnect().block();
    }
}