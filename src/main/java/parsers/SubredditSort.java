package parsers;

public enum SubredditSort {
    HOT ("hot"),
    NEW ("new"),
    TOP ("top"),
    CONTROVERSIAL("controversial");

    private String sort;

    SubredditSort(String sort){
        this.sort = sort;
    }

    public String getSort() {
        return sort;
    }
}
