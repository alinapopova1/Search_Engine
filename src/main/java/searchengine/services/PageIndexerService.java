package searchengine.services;

import searchengine.model.PageEntity;

import java.io.IOException;

public interface PageIndexerService {

    void indexHtml(String html, PageEntity indexingPage) throws IOException;

    void refreshIndex(String html, PageEntity refreshPage) throws IOException;
}
