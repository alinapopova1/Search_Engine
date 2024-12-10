package searchengine.services.interfaces;

import searchengine.dto.indexing.IndexingResponse;

import java.util.concurrent.atomic.AtomicBoolean;

public interface IndexingService {
    void startIndexing(AtomicBoolean statusIndexingProcess);

    IndexingResponse indexPage(String url);
}
