package com.avalutions.lou.manager.net;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.avalutions.lou.manager.models.World;
import com.avalutions.lou.manager.net.requests.Poll;
import com.avalutions.lou.manager.net.requests.Reset;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: benny
 * Date: 9/2/12
 * Time: 2:21 AM
 * To change this template use File | Settings | File Templates.
 */
public class Session implements Reset.ResetCompleteHandler {

    private static Session[] sessions;
    private static Session activeSession;
    private static SessionActivationHandler activationHandler;

    public interface SessionActivationHandler {
        public void onSessionActivated();
    }

    public synchronized static void setActivationHandler(SessionActivationHandler handler) {
        activationHandler = handler;
    }
    public static Session[] getSessions() {
        return sessions;
    }
    public static Session getActive() {
        return activeSession;
    }
    public static boolean login(String username, String password) {
        //authentication block:
        List<BasicNameValuePair> nvps = new ArrayList<BasicNameValuePair>();
        nvps.add(new BasicNameValuePair("mail", username));
        nvps.add(new BasicNameValuePair("password", password));

        UltimaClient.getInstance().post("https://www.lordofultima.com/en/user/login", nvps);

        String html = UltimaClient.getInstance().get("http://www.lordofultima.com/en/game/index");

        Document doc = Jsoup.parse(html);
        Elements forms = doc.select("form");
        Pattern pattern = Pattern.compile("http://prodgame(\\d+)\\.lordofultima.com/(\\d+)/index.aspx");
        Pattern worldPattern = Pattern.compile("World (\\d+) \\((.*)\\)");
        Matcher matcher, worldMatcher;
        List<Session> sessionList = new ArrayList<Session>();
        for(Element element : forms)
        {
            matcher = pattern.matcher(element.attr("action"));
            if(matcher.matches())
            {
                Element input = element.select("input[type=hidden]").first();
                String sessionId = input.attr("value");
                Session session = new Session(sessionId);
                session.setGame(matcher.group(1));
                session.setInstance(matcher.group(2));

                String button = element.select("input[type=submit]").first().attr("value");
                worldMatcher = worldPattern.matcher(button);
                worldMatcher.matches();
                session.setWorldId(worldMatcher.group(1));
                session.setRegion(worldMatcher.group(2));

                sessionList.add(session);
            }
        }
        sessions = sessionList.toArray(new Session[sessionList.size()]);
        return sessionList.size() > 0;
    }

    private String sessionId;
    private String game;
    private String instance;
    private String worldId;
    private String region;
    private World world;
    private final Timer timer = new Timer();

    public World getWorld() {
        return world;
    }

    private Session(String sessionId) {
        this.sessionId = sessionId;
        world = new World();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getGame() {
        return game;
    }

    public void setGame(String game) {
        this.game = game;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getWorldId() {
        return worldId;
    }

    public void setWorldId(String worldId) {
        this.worldId = worldId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void activate() {
        Reset reset = new Reset(this);
        reset.setResetCompleteHandler(this);
        reset.execute();
        if(activeSession != null) {
            activeSession.deactivate();
        }
        activeSession = this;
    }

    private void deactivate() {

    }

    @Override
    public void onResetComplete(boolean result) {
        Log.d("SESSION", "Reset: " + String.valueOf(result));
        if(activationHandler != null) {
            activationHandler.onSessionActivated();
        }

        timer.schedule(pollHandler, 0, 2000);
    }

    private long lastcall = System.currentTimeMillis();
    private long lastcompleted = System.currentTimeMillis();
    private int sequence = 1;
    private TimerTask pollHandler = new TimerTask() {
        @Override
        public void run() {
            synchronized (Session.this) {
                if(lastcall <= lastcompleted) {
                    Poll poll = new Poll(sequence);
                    poll.setPollCompletedHandler(handler);
                    poll.execute();

                    lastcall = System.currentTimeMillis();
                    sequence++;
                }
            }
        }
    };

    private Poll.PollCompletedHandler handler = new Poll.PollCompletedHandler() {
        @Override
        public void onPollCompleted() {
            lastcompleted = System.currentTimeMillis();
        }
    };
}
