package database;

import java.util.List;

public class ArtStationPost {
    private Integer id;
    private String artist;
    private String tags;
    private String permalink;
    private List<String> attachments;

    public ArtStationPost(String artist, String tags, String permalink) {
        this.artist = artist;
        this.tags = tags;
        this.permalink = permalink;
    }

    public ArtStationPost(String artist, String tags, String permalink, List<String> attachments) {
        this.artist = artist;
        this.tags = tags;
        this.permalink = permalink;
        this.attachments = attachments;
    }

    public ArtStationPost(Integer id, String artist, String tags, String permalink, List<String> attachments) {
        this.id = id;
        this.artist = artist;
        this.tags = tags;
        this.permalink = permalink;
        this.attachments = attachments;
    }

    public Integer getId() {
        return id;
    }

    public String getArtist() {
        return artist;
    }

    public String getTags() {
        return tags;
    }

    public String getPermalink() {
        return permalink;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    @Override
    public String toString() {
        return "ArtStationPost{" +
                "artist='" + artist + '\'' +
                ", tags='" + tags + '\'' +
                ", permalink='" + permalink + '\'' +
                ", attachments=" + attachments +
                '}';
    }
}
