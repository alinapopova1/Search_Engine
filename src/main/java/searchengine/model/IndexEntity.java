package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "index_page")
@NoArgsConstructor
@Getter
@Setter
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;
    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity page;
    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaEntity lemma;
    @Column(name = "lemma_rank", nullable = false)
    private int rank;

    public Integer getPageId(){
        return page.getId();
    }
}
