package searchengine.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;

@RequiredArgsConstructor
@Service
public class StorageSite implements CRUDService<SiteEntity> {
    private final SiteRepositories siteRepositories;

    public void deleteById(int id) {

    }

    @Override
    public int deleteAllById(String id) {
        return 0;
    }
}
