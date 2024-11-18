package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.PageEntity;

public interface PageRepositories extends JpaRepository<PageEntity, Integer> {
    @Query(value = "select * from page where site_id = :site_id limit 1", nativeQuery = true)
    PageEntity findBySiteId(@Param("site_id") int siteId);
}
