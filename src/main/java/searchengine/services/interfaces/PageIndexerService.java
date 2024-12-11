package searchengine.services.interfaces;

import searchengine.model.PageEntity;

import java.io.IOException;

public interface PageIndexerService {
    /**
     *
     * @param html новый контент переиндексируемой страницы
     * @param refreshPage существующая в БД страница, которая переиндексируется
     * @throws IOException
     */
    void refreshIndex(String html, PageEntity refreshPage) throws IOException;
}
