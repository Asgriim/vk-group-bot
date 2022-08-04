package app;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import database.ArtStationPost;
import database.DatabaseManager;
import database.RedditPost;
import exceptions.EmptyTableException;
import exceptions.ParsingIssueException;
import parsers.ArtStationParser;
import parsers.RedditParser;
import parsers.SubredditSort;
import vk.Bot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App {
    private static final Logger logger = Logger.getLogger(App.class.getName());

    public void launch() throws InterruptedException {
        Properties properties = new Properties();
        try {
            loadPropertiesFromFile(properties,"private.properties");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "FATAL ERROR: cant load properties", e);
            System.exit(1);
        }
        DatabaseManager databaseManager = new DatabaseManager(properties.getProperty("db.URL"),
                Integer.parseInt(properties.getProperty("db.postInterval")));
        try {
            databaseManager.createConnection();
//            databaseManager.clearPostTime(); // only when reseting posting
            databaseManager.configureDB();
        } catch (SQLException e) {
            logger.log(Level.SEVERE,"FATAL ERROR: can't setup DB\n",e);
        }
        System.out.println("configured db");
        artStationParsing(databaseManager);
        redditParsing(databaseManager,properties.getProperty("re.subreddit.1"),SubredditSort.HOT);
        redditParsing(databaseManager, properties.getProperty("re.subreddit.2"), SubredditSort.HOT);
        Thread.sleep(10000);
        vkGroup(databaseManager);
    }


    public void vkGroup(DatabaseManager databaseManager){
        Runnable vkTask = () -> {
            Properties properties = new Properties();
            try {
                loadPropertiesFromFile(properties,"private.properties");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "FATAL ERROR: can't load properties", e);
                System.exit(1);
            }
            Bot bot = new Bot(
                    Integer.parseInt(properties.getProperty("vk.groupId")),
                    properties.getProperty("vk.groupToken"),
                    Integer.parseInt(properties.getProperty("vk.userId")),
                    properties.getProperty("vk.UserToken")
            );
            String strId = properties.getProperty("vk.strId");
            String groupName = properties.getProperty("vk.groupName");
            Integer afterPostRest = Integer.parseInt(properties.getProperty("vk.afterPostRest"));
            String mes;
            List<File> files;
            ArtStationPost artStationPost;
            RedditPost redditPost;
            Integer postTime;
            Integer defRestTime = Integer.parseInt(properties.getProperty("defaultRestTime"));
            Integer postsCount = 1;
            Integer asDuration = Integer.parseInt(properties.getProperty("as.postDuration"));
            Integer postponedLimit = Integer.parseInt(properties.getProperty("vk.postPonedLimit"));
            Integer reqInterval = Integer.parseInt(properties.getProperty("vk.requestInterval"));
            while (!Thread.currentThread().isInterrupted()){
                try {
                    // TODO: 02.08.2022  refactor plz
                    if(bot.countPostponed() >= postponedLimit) {
                        postTime = databaseManager.getPostTime();
                        if (Instant.now().getEpochSecond() >= postTime) {
                            logger.info(Thread.currentThread().getName() + " going to sleep for " + defRestTime + " seconds");
                            TimeUnit.SECONDS.sleep(defRestTime);
                        } else {
                            logger.info(Thread.currentThread().getName() + " going to sleep for " + (postTime - Instant.now().getEpochSecond()) + " seconds");
                            TimeUnit.SECONDS.sleep(postTime - Instant.now().getEpochSecond());
                        }
                        continue;
                    }
                    if(postsCount % asDuration == 0){
                        artStationPost = databaseManager.getTopOfArtStation();
                        try {
                            files = saveAsFiles(
                                    "as" + artStationPost.getId() + "_%d.jpg",
                                    artStationPost.getAttachments()
                            );
                            mes = artStationPost.getTags() + "\n" + "by " + artStationPost.getArtist() + "\n#artSt@" + strId;
                            bot.postPhotosOnWall(files,
                                    databaseManager.nextPostTime(),
                                    mes,
                                    "сурс: " + artStationPost.getPermalink(),
                                    reqInterval);
                            logger.info(Thread.currentThread().getName() + " posted artSt");
                            deleteFiles(files);
                        }catch (IOException e){
                            logger.log(Level.SEVERE,"ERROR: " , e);
                        }
                        databaseManager.deleteFromDB("artStation",artStationPost.getPermalink());

                    }
                    else {
                        String fileName = "";
                        mes = "#mems@" + strId;
                        redditPost = databaseManager.getTopOfReddit();
                        if (redditPost.getAttachments().get(0).contains(".jpg")) {
                            fileName = "reddit" + redditPost.getId() + "_%d.jpg";
                            try {
                                files = saveAsFiles(
                                        fileName,
                                        redditPost.getAttachments()
                                );
                                bot.postPhotosOnWall(
                                        files,
                                        databaseManager.nextPostTime(),
                                        mes,
                                        "сурс: " + redditPost.getPermalink(),
                                        reqInterval
                                );
                                logger.info(Thread.currentThread().getName() + " posted reddit");
                                deleteFiles(files);
                            }catch (IOException e){
                                logger.log(Level.SEVERE,"ERROR: " , e);
                            }

                        }
                        else if (redditPost.getAttachments().get(0).contains(".gif")) {
                            fileName = "reddit" + redditPost.getId() + "_%d.gif";
                            try {
                                files = saveAsFiles(
                                        fileName,
                                        redditPost.getAttachments()
                                );
                                bot.postDocOnwall(files.get(0), groupName + "_" + redditPost.getId(),
                                        databaseManager.nextPostTime(),
                                        mes,
                                        reqInterval);
                                logger.info(Thread.currentThread().getName() + " posted reddit");
                                deleteFiles(files);
                            }catch (IOException e){
                                logger.log(Level.SEVERE,"ERROR: " , e);
                            }
                        }
                        databaseManager.deleteFromDB("reddit",redditPost.getPermalink());
                    }
                    postsCount += 1;
                    logger.info(Thread.currentThread().getName() + " going to sleep for " + afterPostRest + " seconds");
                    TimeUnit.SECONDS.sleep(afterPostRest);
                } catch (ClientException | ApiException | SQLException | InterruptedException |
                         EmptyTableException e) {
                    logger.log(Level.SEVERE,"ERROR: " , e);
                    try {
                        TimeUnit.SECONDS.sleep(defRestTime);
                    } catch (InterruptedException ex) {
                        e.printStackTrace();
                        System.exit(1); //??7!?
                    }
                    continue;
                }
            }
        };
        Thread vkThread = new Thread(vkTask);
        vkThread.setName("vkGroup thread");
        vkThread.start();
    }

    public void redditParsing(DatabaseManager databaseManager, String subreddit, SubredditSort sortMode) {
        Runnable reTask = () -> {
            Properties properties = new Properties();
            try {
                loadPropertiesFromFile(properties,"private.properties");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "FATAL ERROR: can't load properties", e);
                System.exit(1);
            }
            RedditParser redditParser = new RedditParser(
                    properties.getProperty("re.appId"),
                    properties.getProperty("re.appSecret"),
                    properties.getProperty("re.userAgent"),
                    properties.getProperty("re.userName"),
                    properties.getProperty("re.password")
            );
            Long startTime = Instant.now().getEpochSecond();
            try {
                redditParser.setSubreddit(subreddit);
                redditParser.start(sortMode);
            } catch (ParsingIssueException e) {
                logger.log(Level.SEVERE, "FATAL ERROR: ", e);
                System.exit(1);
            }
            Integer minScore = Integer.parseInt(properties.getProperty("re.MinScore"));
            Integer rowLimit = Integer.parseInt(properties.getProperty("re.tableRowsLimit"));
            Integer postTime;
            Integer defRestTime = Integer.parseInt(properties.getProperty("defaultRestTime"));
            String permalink;
            String title;
            String score;
            String attachment;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if ((Instant.now().getEpochSecond() - startTime) >= 3500) {
                        redditParser.setAuthToken(redditParser.getAuthToken());
                    }
                    if (redditParser.next()) {
                        // TODO: 02.08.2022 refactor later
                        if (databaseManager.countRowsInReddit() >= rowLimit) {
                            postTime = databaseManager.getPostTime();
                            if (Instant.now().getEpochSecond() >= postTime) {
                                logger.info(Thread.currentThread().getName() + " going to sleep for " + defRestTime + " seconds");
                                TimeUnit.SECONDS.sleep(defRestTime);
                            } else {
                                logger.info(Thread.currentThread().getName() + " going to sleep for " + (postTime - Instant.now().getEpochSecond()) + " seconds");
                                TimeUnit.SECONDS.sleep(postTime - Instant.now().getEpochSecond());
                            }
                            redditParser.start(sortMode);
                            continue;
                        }
                        permalink = redditParser.getPermalink();
                        title = redditParser.getTitle();
                        score = redditParser.getScore();
                        attachment = redditParser.getAttachmentLink();
                        if((!attachment.contains(".jpg") && !attachment.contains(".gif")) || databaseManager.isInGarbage(permalink) || Integer.parseInt(score) < minScore)
                            continue;
                        databaseManager.insertToReddit(title,score,permalink,attachment);
                        logger.info(Thread.currentThread().getName() + " inserted reddit post in db: " + permalink);
                        databaseManager.insertToGarbage(permalink);
                        Thread.sleep(40);
                        continue;
                    }
                    logger.info(Thread.currentThread().getName() + " going to sleep for 30 seconds");
                    TimeUnit.SECONDS.sleep(30);
                    redditParser.goToNextPage(sortMode,99);
                } catch (SQLException | InterruptedException | ParsingIssueException e) {
                    logger.log(Level.SEVERE,"ERROR: " , e);
                    try {
                        TimeUnit.SECONDS.sleep(defRestTime);
                    } catch (InterruptedException ex) {
                        e.printStackTrace();
                        System.exit(1); //??7!?
                    }
                    continue;
                }
            }


        };
        Thread reThread = new Thread(reTask);
        reThread.setName("reddit thread:  " + subreddit);
        reThread.start();
    }

    public void artStationParsing(DatabaseManager databaseManager) {
        Runnable aSTask = () -> {
            ArtStationParser artStationParser = new ArtStationParser();
            Properties properties = new Properties();
            try {
                artStationParser.start();
                loadPropertiesFromFile(properties,"private.properties");
            } catch (ParsingIssueException | IOException e) {
                logger.log(Level.SEVERE, "FATAL ERROR: can't load properties", e);
                System.exit(1);
            }
            Integer defRestTime = Integer.parseInt(properties.getProperty("defaultRestTime"));
            Integer time;
            List<String> attachments;
            String permalink;
            String tags;
            String artist;
            Integer rowLimit = Integer.parseInt(properties.getProperty("as.TableRowsLimit"));
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (artStationParser.next()) {
                        if (databaseManager.countRowsInAS() >= rowLimit) {
                            // TODO: 02.08.2022 refactor later
                            time = databaseManager.getPostTime();
                            if (Instant.now().getEpochSecond() >= time){
                                logger.info(Thread.currentThread().getName() + " going to sleep for " + defRestTime + " seconds");
                                TimeUnit.SECONDS.sleep(defRestTime);
                            }
                            else {
                                logger.info(Thread.currentThread().getName() + " going to sleep for " + (time - Instant.now().getEpochSecond()) + " seconds");
                                TimeUnit.SECONDS.sleep(time - Instant.now().getEpochSecond());
                            }
                            artStationParser.reset();
                            continue;
                        }
                        attachments = artStationParser.getAttachmentsLinks(".jpg", 9);
                        permalink = artStationParser.getSourceLink();
                        tags = artStationParser.getTags();
                        artist = artStationParser.getArtist();
                        if (attachments.isEmpty() || databaseManager.isInGarbage(permalink))
                            continue;

                        for (var attachment: attachments) {
                            databaseManager.insertToAS(artist,tags,permalink,attachment);
                        }
                        logger.info(Thread.currentThread().getName() + " inserted AS post in db: " + permalink);
                        databaseManager.insertToGarbage(permalink);
                        continue;
                    }
                    logger.info(Thread.currentThread().getName() + " going to sleep for " + 30 + " seconds"); //lol
                    TimeUnit.SECONDS.sleep(30);
                    artStationParser.goToNextPage();
                } catch (SQLException | ParsingIssueException | InterruptedException e) {
                    logger.log(Level.SEVERE,"ERROR: " , e);
                    try {
                        TimeUnit.SECONDS.sleep(defRestTime);
                    } catch (InterruptedException ex) {
                        e.printStackTrace();
                        System.exit(1); //????
                    }
                    continue;
                }
            }

        };
        Thread artStation = new Thread(aSTask);
        artStation.setName("artStation thread");
        artStation.start();
    }

    public File saveAsFile(String fileName, String link) throws IOException {
        try (ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(link).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(fileName)
        ){
            fileOutputStream.getChannel().transferFrom(readableByteChannel,0,Long.MAX_VALUE);
        }
        return new File(fileName);
    }

    public List<File> saveAsFiles(String formatableFileName,List<String> links) throws IOException, InterruptedException {
        List<File> out = new ArrayList<>();
        for (int i = 0; i < links.size(); i++){
            out.add(saveAsFile(String.format(formatableFileName,i),links.get(i)));
            Thread.sleep(100);
        }
        return out;
    }

    public void deleteFiles(List<File> files) throws IOException {
        for (var file:files) {
            Files.deleteIfExists(file.toPath());
        }
    }

    public static synchronized Properties loadPropertiesFromFile(Properties properties, String fileName) throws IOException {
        properties.load(new FileReader(fileName));
        return properties;
    }
}
