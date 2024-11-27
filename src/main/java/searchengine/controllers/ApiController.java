package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;
    private final AtomicBoolean statusIndexingProcess = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ApiController(StatisticsService statisticsService, IndexingService indexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        if (statusIndexingProcess.get()) {
            IndexingResponse indexingResponse = new IndexingResponse();
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация уже запущена");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(indexingResponse);
        }
        try {
            return ResponseEntity.ok(executorService.submit(() -> {
                statusIndexingProcess.set(true);
                return indexingService.startIndexing(statusIndexingProcess);
            }).get());
        } catch (ExecutionException | InterruptedException exception) {
            IndexingResponse response = new IndexingResponse();
            response.setResult(false);
            response.setError("Error: " + exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        if (!statusIndexingProcess.get()) {
            IndexingResponse indexingResponse = new IndexingResponse();
            indexingResponse.setResult(false);
            indexingResponse.setError("Индексация не запущена");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(indexingResponse);
        } else {
            statusIndexingProcess.set(false);
            IndexingResponse indexingResponse = new IndexingResponse();
            indexingResponse.setResult(false);
            return ResponseEntity.ok(indexingResponse);
        }
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam String url) {
        IndexingResponse indexingResponse = indexingService.indexPage(url);
        if (!indexingResponse.getResult()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(indexingResponse);
        } else {
            return ResponseEntity.ok(indexingResponse);
        }
    }


    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam String query, @RequestParam(required = false) String site, @RequestParam(required = false, defaultValue = "0") int offset, @RequestParam(required = false, defaultValue = "20") int limit) {
        return ResponseEntity.ok(searchService.search(query, site, offset, limit));
    }
}
