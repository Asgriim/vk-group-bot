package parsers;

import exceptions.ParsingIssueException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ArtStationParser {
    private Integer currPage;
    private String mainPageLink;
    private String currentPostJsonLink;
    private JSONObject currentPostJson;
    private HttpURLConnection mainConnection;
    private JSONObject mainJson;
    private JSONArray jsonData;
    private Iterator jsonArrayIterator;
    private static final Logger logger = Logger.getLogger(ArtStationParser.class.getName());

    public ArtStationParser() {
        this.mainPageLink = "https://www.artstation.com/projects.json?page=%d&sorting=trending";
        currPage = 1;
    }

    private HttpURLConnection createConnection(String link) throws IOException {
        logger.fine("opening new connection: " + link);
        return (HttpURLConnection) new URL(link).openConnection();
    }

    private HttpURLConnection createMainConnection() throws ParsingIssueException {
        try {
            mainConnection = createConnection(String.format(mainPageLink,currPage));
//            mainConnection = createConnection(mainPageLink);
            logger.info("created main connection");
            return mainConnection;
        } catch (IOException e) {
            logger.log(Level.SEVERE,"Exception: ",e);
            throw new ParsingIssueException("ArtStation main connection problem\n" + e.getMessage());
        }
    }

    public String getArtist(){
        JSONObject tempObject = (JSONObject) currentPostJson.get("user");
        return (String) tempObject.get("full_name");
    }

    public List getAttachmentsLinks(String fileExtension, int limit){
        List<JSONObject> tempArray = (JSONArray) currentPostJson.get("assets");
        return tempArray.stream().filter(x -> x.get("asset_type").equals("image"))
                .map(x -> (String)x.get("image_url"))
                .filter(x -> x.contains(fileExtension))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void getMainJSONData() throws ParsingIssueException {
        logger.fine("trying to get main JSON data");
        try (InputStreamReader reader = new InputStreamReader(createMainConnection().getInputStream())) {
            currPage = 1;
            mainJson = (JSONObject) JSONValue.parseWithException(reader);
            jsonData = (JSONArray) mainJson.get("data");
            jsonArrayIterator = jsonData.iterator();
            logger.info("received main JSON data");
        } catch (IOException | ParseException e) {
            logger.log(Level.SEVERE,"Exception: ",e);
            throw new ParsingIssueException(e.getMessage());
        }
    }

    public String getSourceLink(){
        return (String) currentPostJson.get("permalink");
    }

    public String getTags(){
        List<String> tempList = (JSONArray) currentPostJson.get("tags");
        return tempList.stream().map(x -> x.replace("\"","").replace("#","").replace(" ","_"))
                .map(x -> "#" + x)
                .collect(Collectors.joining(", "));
    }

    public void goToNextPage() throws ParsingIssueException {
        currPage += 1;
//        mainPageLink = mainPageLink.replace(currPage.toString(),String.valueOf(currPage + 1));
        logger.info("going to next page: " + String.format(mainPageLink,currPage));
        getMainJSONData();
        // TODO: 16.07.2022 добавить нехт?
    }

    public boolean next() throws ParsingIssueException {
        if (jsonArrayIterator.hasNext()){
            logger.fine("trying to get next post");
            JSONObject object = (JSONObject) jsonArrayIterator.next();
            currentPostJsonLink = String.format("https://www.artstation.com/projects/%s.json",object.get("hash_id"));
            try {
                currentPostJson = receiveJSONFromLink(currentPostJsonLink);
                logger.info("parsing new post: " + getSourceLink());
            } catch (IOException e) {
                logger.log(Level.SEVERE,"Exception: ",e);
                throw new ParsingIssueException(e.getMessage());
            }
            return true;
        }
        logger.info("out of data on current page");
        return false;
    }

    public JSONObject receiveJSONFromLink(String JSONlink) throws IOException {
        logger.fine("trying to receive json from: " + JSONlink);
        try (InputStreamReader reader = new InputStreamReader(createConnection(JSONlink).getInputStream())){
            return  (JSONObject) JSONValue.parse(reader);
        }
    }

    public void reset() {
        logger.info("resetting artStation parser");
        currPage = 1;
        setMainPageLink(String.format(mainPageLink,currPage));
        try {
            start();
        } catch (ParsingIssueException e) {
            logger.log(Level.SEVERE,"Exception: ",e);
        }
    }

    public void start() throws ParsingIssueException {
        logger.info("starting artStation parsing");
        createMainConnection();
        getMainJSONData();
//        next();
    }

    public void setCurrentPostJsonLink(String currentPostJsonLink) {
        this.currentPostJsonLink = currentPostJsonLink;
    }

    public void setCurrentPostJson(JSONObject currentPostJson) {
        this.currentPostJson = currentPostJson;
    }

    public void setCurrPage(Integer currPage) {
        this.currPage = currPage;
    }

    public void setMainPageLink(String mainPageLink) {
        this.mainPageLink = mainPageLink;
    }

}
