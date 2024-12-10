package searchengine.services.interfaces;

import searchengine.dto.search.SearchResponse;

import java.io.IOException;

public interface SearchService {
    SearchResponse search(String query, String site, int offset, int limit) throws IOException;
}
