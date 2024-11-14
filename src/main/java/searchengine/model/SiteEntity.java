package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "site")
@NoArgsConstructor
@Getter
@Setter
public class SiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
//    @Nonnull
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private StatusSite status;
    @Column(name = "status_time", columnDefinition = "DATETIME", nullable = false)
//    @Nonnull
    private LocalDateTime statusTime;
    @Column(name = "last_error", columnDefinition = "TEXT", nullable = true)
    private String lastError;
//    @Nonnull
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;
//    @Nonnull
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;
}
