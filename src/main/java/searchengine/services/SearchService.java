package searchengine.services;

import org.springframework.web.bind.annotation.RequestParam;
import searchengine.dto.search.SearchResponse;

import java.io.IOException;

public interface SearchService {
    SearchResponse search(String query, String site, int offset, int limit) throws IOException;
}
