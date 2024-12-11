package searchengine.dto.statistics;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private String status;
    private Timestamp statusTime;
    private String error;
    private int pages;
    private int lemmas;
}
