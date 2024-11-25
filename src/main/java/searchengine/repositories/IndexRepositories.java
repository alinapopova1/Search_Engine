package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

public interface IndexRepositories extends JpaRepository<IndexEntity, Integer> {

    @Query(value = "select * from page where page_id = :page_id and lemma_id = :lemma_id limit 1", nativeQuery = true)
    IndexEntity findByPageIdLemmaId(@Param("page_id") int pageId, @Param("lemma_id") int lemmaId);
}
