package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "site", indexes = {@Index(columnList = "url", name = "url_index")})
@NoArgsConstructor
@Getter
@Setter
public class SiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private int id;
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private String status;
    @Column(name = "status_time", columnDefinition = "DATETIME", nullable = false)
    private Timestamp statusTime;
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;
    @Column(columnDefinition = "VARCHAR(255)", nullable = false )
    private String name;
}
