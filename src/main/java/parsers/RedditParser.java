package parsers;

import exceptions.ParsingIssueException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RedditParser {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String mainLink = "https://www.reddit.com";
    private String authToken;
    private final String appId;
    private final String appSecret;
    private final String userAgent;
    private final String userName;
    private final String userPassword;
    private String after;
    private final String requestsURL = "https://oauth.reddit.com/";
    private String Subreddit;
    private JSONArray postsArray;
    private Iterator postsArrayIterator;
    private JSONObject currPost;
    private static final Logger logger = Logger.getLogger(RedditParser.class.getName());

    public RedditParser(String appId, String appSecret, String userAgent, String userName, String userPassword) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.userAgent = userAgent;
        this.userName = userName;
        this.userPassword = userPassword;
    }

    public boolean next(){
        if(postsArrayIterator.hasNext()){
            JSONObject tempObject = (JSONObject) postsArrayIterator.next();
            currPost = (JSONObject) tempObject.get("data");
            logger.info("parsing next post " + getPermalink());
            return true;
        }
        return false;
    }

    public String getAuthToken() throws ParsingIssueException {
        logger.fine("trying to get auth token");
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(appId, appSecret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.put("User-Agent",
                Collections.singletonList(userAgent));
        String body = "grant_type=password&username=" + userName + "&password=" + userPassword;
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        String authUrl = "https://www.reddit.com/api/v1/access_token";
        ResponseEntity<String> response = restTemplate.postForEntity(
                authUrl, request, String.class);
        JSONObject jsonObject = null;
        try {
            jsonObject = (JSONObject) JSONValue.parseWithException(response.getBody());
        } catch (ParseException e) {
            logger.log(Level.SEVERE,"Exception: ",e);
            throw new ParsingIssueException("Something wrong with auth token\n" + e.getMessage());
        }
        logger.info("received auth token");
        return (String) jsonObject.get("access_token");
    }

    public void goToNextPage(SubredditSort sortMode,Integer limit) throws ParsingIssueException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        headers.put("User-Agent",
                Collections.singletonList(userAgent));
        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
        String url = requestsURL + Subreddit + sortMode.getSort() + "?" +"after=" + after +
                "&limit=" + limit.toString();
        JSONObject jsonResponse = getRequest(entity,url);
        JSONObject mainData = (JSONObject) jsonResponse.get("data");
        after = (String) mainData.get("after");
        postsArray = (JSONArray) mainData.get("children");
        postsArrayIterator = postsArray.iterator();
        next();
    }

    public String getPermalink(){
        return mainLink + currPost.get("permalink");
    }

    public String getScore(){
        return String.valueOf(currPost.get("score"));
    }

    public String getAttachmentLink(){
        return (String) currPost.get("url");
    }

    public String getTitle(){
        return (String) currPost.get("title");
    }

    public JSONObject getRequest( HttpEntity<String> entity, String url) throws ParsingIssueException {
        ResponseEntity<String> response
                = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        try {
            return (JSONObject) JSONValue.parseWithException(response.getBody());
        } catch (ParseException e) {
            logger.log(Level.SEVERE,"Exception: ",e);
            throw new ParsingIssueException("something wrong with get request\n" + e.getMessage());
        }
    }

    public void setSubreddit(String subreddit) {
        this.Subreddit = "r/"+ subreddit +"/";
    }

    public void start(SubredditSort sortMode) throws ParsingIssueException {
        logger.info("starting parsing");
        authToken = getAuthToken();
        if (Subreddit == null) throw new ParsingIssueException("sibreddit not specified");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        headers.put("User-Agent",
                Collections.singletonList(userAgent));
        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
        String url = requestsURL + Subreddit + sortMode.getSort() + "/";
        JSONObject jsonResponse = getRequest(entity,url);
        JSONObject mainData = (JSONObject) jsonResponse.get("data");
        after = (String) mainData.get("after");
        postsArray = (JSONArray) mainData.get("children");
        postsArrayIterator = postsArray.iterator();
        next();
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
}
