package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.SiteEntity;

public interface SiteRepositories extends JpaRepository<SiteEntity, Long> {
}
