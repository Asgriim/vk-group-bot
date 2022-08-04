package database;

import java.util.List;

public class RedditPost {
    private Integer id;
    private String title;
    private String score;
    private String permalink;
    private List<String> attachments;

    public RedditPost(String title, String score, String permalink, List<String> attachments) {
        this.title = title;
        this.score = score;
        this.permalink = permalink;
        this.attachments = attachments;
    }

    public RedditPost(Integer id, String title, String score, String permalink, List<String> attachments) {
        this.id = id;
        this.title = title;
        this.score = score;
        this.permalink = permalink;
        this.attachments = attachments;
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getScore() {
        return score;
    }

    public String getPermalink() {
        return permalink;
    }

    public List<String> getAttachments() {
        return attachments;
    }
}
