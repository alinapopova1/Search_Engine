package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

public interface SiteRepositories extends JpaRepository<SiteEntity, Integer> {
    @Query(value = "select * from site where url = :url", nativeQuery = true)
    SiteEntity findByUrl(@Param("url") String url);

}
