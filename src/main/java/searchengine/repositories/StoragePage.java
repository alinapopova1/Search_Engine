package searchengine.repositories;

import lombok.RequiredArgsConstructor;
import searchengine.model.PageEntity;

@RequiredArgsConstructor
public class StoragePage implements CRUDService<PageEntity> {
    PageRepositories pageRepositories;

    @Override
    public int deleteAllById(String id) {
//        pageRepositories.delete();
        return 1;
    }
}
