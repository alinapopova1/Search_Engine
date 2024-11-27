package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;

public interface PageRepositories extends JpaRepository<PageEntity, Integer> {
    @Query(value = "select * from page where site_id = :site_id limit 1", nativeQuery = true)
    PageEntity findBySiteId(@Param("site_id") int siteId);

    @Query(value = "select * from page where site_id = :site_id", nativeQuery = true)
    List<PageEntity> findAllBySiteId(@Param("site_id") int siteId);

    @Query(value = "select * from page where site_id = :site_id and path = :path limit 1", nativeQuery = true)
    PageEntity findBySiteIdAndPage(@Param("site_id") int siteId, @Param("path") String path);

    @Query(value = "select * from page where id in :page_ids", nativeQuery = true)
    List<PageEntity> findByPageIds(@Param("page_ids") List<Integer> pageIds);

}
