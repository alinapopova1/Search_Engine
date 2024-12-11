package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;

import java.util.List;
@Transactional()
public interface IndexRepositories extends JpaRepository<IndexEntity, Integer> {

    @Query(value = "select * from index_page where page_id = :page_id and lemma_id = :lemma_id ", nativeQuery = true)
    IndexEntity findByPageIdLemmaId(@Param("page_id") int pageId, @Param("lemma_id") int lemmaId);

    @Query(value = "select * from index_page where lemma_id = :lemma_id ", nativeQuery = true)
    List<IndexEntity> findByLemmaId(@Param("lemma_id") int lemmaId);

    @Query(value = "select * from index_page where page_id in :page_ids and lemma_id = :lemma_id ", nativeQuery = true)
    List<IndexEntity> findByPageIdsLemmaId(@Param("page_ids") List<Integer> pageIds, @Param("lemma_id") int lemmaId);

    @Modifying
    @Transactional
    @Query(value = "delete from index_page where page_id = :page_id", nativeQuery = true)
    void deleteByPageId(@Param("page_id") int pageId);

    @Query(value = "select * from index_page where page_id = :page_id", nativeQuery = true)
    List<IndexEntity> findAllByPageId(@Param("page_id") int pageId);

}
