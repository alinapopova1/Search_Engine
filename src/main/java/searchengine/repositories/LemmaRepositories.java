package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

public interface LemmaRepositories extends JpaRepository<LemmaEntity, Integer> {

    @Query(value = "select * from lemma where site_id = :site_id", nativeQuery = true)
    List<LemmaEntity> findBySiteId(@Param("site_id") int siteId);

    @Query(value = "select * from lemma where site_id = :site_id and lemma = :lemma limit 1", nativeQuery = true)
    LemmaEntity findBySiteIdAndLemma(@Param("site_id") int siteId, @Param("lemma") String lemma);
}
