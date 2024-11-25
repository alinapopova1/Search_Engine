package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.util.concurrent.atomic.AtomicBoolean;

public interface IndexingService {
    IndexingResponse startIndexing(AtomicBoolean statusIndexingProcess);

    IndexingResponse indexPage(String url);
}
