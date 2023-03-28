package database;

import exceptions.EmptyTableException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    Connection connection;
    String dbURL;
    Integer postInterval;

    public DatabaseManager(String dbURL, Integer postsInterval) {
        this.dbURL = dbURL;
        this.postInterval = postsInterval;
    }

    public void createConnection() throws SQLException {
        connection = DriverManager.getConnection(dbURL);
    }

    public void configureDB() throws SQLException {
        try (Statement statement = connection.createStatement();) {
            statement.execute("create table if not exists artStation (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    artist TEXT," +
                    "    tags TEXT," +
                    "    permalink TEXT," +
                    "    attachment TEXT" +
                    ")");

            statement.execute("create table if not exists reddit(" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    title TEXT," +
                    "    score TEXT," +
                    "    permalink TEXT," +
                    "    attachment TEXT" +
                    ")");

            statement.execute("create table if not exists garbage(" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    permalink TEXT" +
                    ")");

            statement.execute("create table if not exists postTime(" +
                    "    time INTEGER" +
                    ")");
            if (isPostTimeEmpty()) {
                statement.execute("insert into postTime values (" + Instant.now().getEpochSecond() + ")");
                return;
            }
            int postTime = getPostTime();
            if (postTime <= Instant.now().getEpochSecond()) {
                clearPostTime();
                statement.execute("insert into postTime values (" + Instant.now().getEpochSecond() + ")");
            }
        }
    }

    public synchronized void insertToAS(String artist, String tags, String permalink, String attachment) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "insert into artStation (artist, tags, permalink, attachment) values (?,?,?,?)")) {
            preparedStatement.setString(1, artist);
            preparedStatement.setString(2, tags);
            preparedStatement.setString(3, permalink);
            preparedStatement.setString(4, attachment);
            preparedStatement.execute();
        }
    }

    public synchronized void insertToReddit(String title, String score, String permalink, String attachment) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "insert into reddit (title, score, permalink, attachment) VALUES (?,?,?,?)")) {
            preparedStatement.setString(1, title);
            preparedStatement.setString(2, score);
            preparedStatement.setString(3, permalink);
            preparedStatement.setString(4, attachment);
            preparedStatement.execute();
        }
    }

    public synchronized void insertToGarbage(String permalink) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement("insert into garbage (permalink) values (?)")) {
            preparedStatement.setString(1, permalink);
            preparedStatement.execute();
        }
    }

    public synchronized ArtStationPost getTopOfArtStation() throws SQLException, EmptyTableException {
        if (countRowsInAS() == 0) throw new EmptyTableException("no data in artStation table");
        try (Statement statement = connection.createStatement();) {
            ResultSet resultSet = statement.executeQuery("select * from artStation order by id limit 12");
            resultSet.next();
            Integer id = resultSet.getInt("id");
            String permalink = resultSet.getString("permalink");
            String artist = resultSet.getString("artist");
            String tags = resultSet.getString("tags");
            ResultSet prepResult = statement.executeQuery("select * from artStation where permalink = '" + permalink + "' order by id");
            List<String> attachments = new ArrayList<>();
            while (prepResult.next()) {
                attachments.add(prepResult.getString("attachment"));
            }
            return new ArtStationPost(id, artist, tags, permalink, attachments);
        }
    }

    public synchronized RedditPost getTopOfReddit() throws SQLException, EmptyTableException {
        if (countRowsInReddit() == 0) throw new EmptyTableException("no data in reddit table");
        try (Statement statement = connection.createStatement();
        ) {
            ResultSet resultSet = statement.executeQuery("select * from reddit order by id limit 5");
            resultSet.next();
            Integer id = resultSet.getInt("id");
            String permalink = resultSet.getString("permalink");
            String title = resultSet.getString("title");
            String score = resultSet.getString("score");
            List<String> attachments = new ArrayList<>();
            attachments.add(resultSet.getString("attachment"));
            return new RedditPost(id, title, score, permalink, attachments);
        }
    }

    public synchronized Integer getPostTime() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery("select time from postTime");
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    public synchronized boolean isPostTimeEmpty() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery("select COUNT(*) from postTime");
            if (resultSet.getInt(1) > 0)
                return false;
            return true;
        }
    }

    public synchronized Integer nextPostTime() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            Integer currTime = getPostTime();
            clearPostTime();
            statement.execute("insert into postTime (time) values (" + (currTime + postInterval) + ")");
            return currTime + postInterval;
        }
    }

    public synchronized void clearPostTime() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("delete from postTime where time > 0");
        }
    }

    public synchronized void deleteFromDB(String tableName, String permalink) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("delete from " + tableName + " where permalink = ?");
        statement.setString(1, permalink);
        statement.execute();
    }

    public synchronized boolean isInGarbage(String permalink) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement("select COUNT(*) from garbage where permalink = ?")) {
            preparedStatement.setString(1, permalink);
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            if (resultSet.getInt(1) > 0) return true;
            return false;
        }
    }

    public synchronized Integer countRowsInReddit() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery("select COUNT(*) from reddit");
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    public synchronized Integer countRowsInAS() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery("select COUNT(*) from artStation");
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

}
