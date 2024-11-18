package searchengine.repositories;

import java.util.Collection;

public interface CRUDService<T> {
//    T getById(Long id);
//    Collection<T> getAll();
//    T create(T news);
//    T update(T news);
    int deleteAllById(String id);
}
