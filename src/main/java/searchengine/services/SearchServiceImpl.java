package searchengine.services;


import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepositories;
import searchengine.repositories.LemmaRepositories;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final SiteRepositories siteRepositories;
    private final PageRepositories pageRepositories;
    private final IndexRepositories indexRepositories;
    private final LemmaRepositories lemmaRepositories;

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) throws IOException {
        List<SiteEntity> siteEntities = getSiteEntities(site);
        LemmaFinder lemmaFinder = LemmaFinder.getRusInstance();
        List<LemmaEntity> allLemmas = getAllLemmaByQuery(query, siteEntities, lemmaFinder);

        allLemmas.removeIf(lemma -> {
            return lemma.getFrequency() > 100;
        });


        SearchResponse searchResponse = new SearchResponse();
        if (allLemmas.isEmpty()) {
            return getSearchResponseEmpty(searchResponse);
        }

        List<LemmaEntity> sortedLemmas = getSortedLemmasByFrequency(allLemmas);

        List<IndexEntity> indexEntities = new ArrayList<>();
        List<Integer> pageIdsToSave = new ArrayList<>();
        for (LemmaEntity lemmaEntity : sortedLemmas) {
//            if (lemmaEntity == sortedLemmas.get(0)){
//                indexEntities = indexRepositories.findByLemmaId(sortedLemmas.get(0).getId());
//                pageIdsToSave.addAll(getPageIdsFromIndex(indexEntities));
//            } else {
            indexEntities = indexRepositories.findByPageIdsLemmaId(pageIdsToSave, lemmaEntity.getId());
            pageIdsToSave.addAll(getPageIdsFromIndex(indexEntities));
//            }

            if (indexEntities.isEmpty() || pageIdsToSave.isEmpty()) {
                break;
            }
        }

        if (pageIdsToSave.isEmpty()) {
            return getSearchResponseEmpty(searchResponse);
        }


        List<PageEntity> pageEntities = pageRepositories.findByPageIds(pageIdsToSave);
        HashMap<PageEntity, Float> absRelevantPages = calculateAllAbsRelevant(pageEntities, indexEntities);
        HashMap<PageEntity, Float> relevantPages = calculateAllRelevant(absRelevantPages);
        List<SearchData> resultSearchDates = generateResultSearchDates(relevantPages, siteEntities, lemmaFinder, sortedLemmas);

        searchResponse.setResult(true);
        searchResponse.setCount(resultSearchDates.size());
        searchResponse.setData(getSortedSearchDataWithOffset(resultSearchDates, offset, limit));

        return searchResponse;
    }

    private static SearchResponse getSearchResponseEmpty(SearchResponse searchResponse) {
        searchResponse.setResult(true);
        searchResponse.setCount(0);
        return searchResponse;
    }

    private static List<SearchData> generateResultSearchDates(HashMap<PageEntity, Float> relevantPages, List<SiteEntity> siteEntities, LemmaFinder lemmaFinder, List<LemmaEntity> sortedLemmas) {
        List<SearchData> resultSearchDates = new ArrayList<>();
        SearchData searchData = new SearchData();
        for (PageEntity pageEntity : relevantPages.keySet()) {
            Document document = Jsoup.parse(pageEntity.getContent());
            searchData.setUri(pageEntity.getPath());
            SiteEntity searchSite = new SiteEntity();
            for (SiteEntity siteEntity : siteEntities) {
                if (siteEntity.getId() == pageEntity.getSite().getId()) {
                    searchSite = siteEntity;
                }
            }
            for (StringBuilder snippet: getSnippets(document, lemmaFinder, sortedLemmas)){
                searchData.setSite(searchSite.getUrl());
                searchData.setTitle(document.title());
                searchData.setSiteName(searchSite.getName());
                searchData.setSnippet(snippet.toString());
                searchData.setRelevance(relevantPages.get(pageEntity));
                resultSearchDates.add(searchData);
            }
        }

        return resultSearchDates;
    }

    private static List<StringBuilder> getSnippets(Document document, LemmaFinder lemmaFinder, List<LemmaEntity> sortedLemmas){
        List<StringBuilder> result = new ArrayList<>();
        List<String> nameLemmas = new ArrayList<>(sortedLemmas.stream().map(LemmaEntity::getLemma).toList());
        List<String> sentences = document.body().getElementsMatchingOwnText("\\p{IsCyrillic}").stream().map(Element::text).toList();
        for (String sentence :sentences){
            StringBuilder textFromElement = new StringBuilder(sentence);
            List<String> words= List.of(sentence.split("[\s:punct]"));
            int searchWords=0;
            for (String word: words){
                String lemmaFromWords = lemmaFinder.getLemmaByWord(word.replaceAll("\\p{Punct}", ""));
                if (nameLemmas.contains(lemmaFromWords)){
                    markWord(textFromElement, word, 0);
                    searchWords++;
                }
            }
            if (searchWords!=0){
                result.add(textFromElement);
            }
        }
        return result;
    }

    private static List<SearchData> getSortedSearchDataWithOffset(List<SearchData> resultSearchDates, int offset, int limit) {
        List<SearchData> sortedSearchDates = resultSearchDates.stream().sorted(Comparator.comparingDouble(SearchData::getRelevance).reversed()).toList();
        List<SearchData> result = new ArrayList<>();
        for (int i = limit * offset; i <= offset * limit + limit; i++) {
            try {
                result.add(sortedSearchDates.get(i));
            }catch (IndexOutOfBoundsException exception){
                break;
            }
        }
        return result;
    }

    private static HashMap<PageEntity, Float> calculateAllRelevant(HashMap<PageEntity, Float> absRelevantPages) {
        Float maxRelevant = absRelevantPages.values().stream().max(Float::compareTo).get();
        HashMap<PageEntity, Float> relevantPages = new HashMap<>();
        for (PageEntity indexPage : absRelevantPages.keySet()) {
            relevantPages.put(indexPage, absRelevantPages.get(indexPage) / maxRelevant);
        }
        return relevantPages;
    }

    private static HashMap<PageEntity, Float> calculateAllAbsRelevant(List<PageEntity> pageEntities, List<IndexEntity> indexEntities) {
        List<IndexEntity> indexEntitiesByPage;
        HashMap<PageEntity, Float> absRelevantPages = new HashMap<>();
        for (PageEntity pageEntity : pageEntities) {
            indexEntitiesByPage = indexEntities.stream().filter(indexEntity -> indexEntity.getPageId() == pageEntity.getId()).toList();
            float relevant = 0;
            for (IndexEntity indexEntity : indexEntitiesByPage) {
                relevant += indexEntity.getRank();
            }
            absRelevantPages.put(pageEntity, relevant);
        }
        return absRelevantPages;
    }

    private static List<Integer> getPageIdsFromIndex(List<IndexEntity> indexEntities) {
        List<Integer> pageToSave = new ArrayList<>();
        for (IndexEntity indexEntity : indexEntities) {
            pageToSave.add(indexEntity.getPageId());
        }
        return pageToSave;
    }

    private static List<LemmaEntity> getSortedLemmasByFrequency(List<LemmaEntity> allLemmas) {
        return allLemmas.stream()
                .map(lemmaEntity -> new AbstractMap.SimpleEntry<>(lemmaEntity.getFrequency(), lemmaEntity))
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(Map.Entry::getValue).toList();
    }

    private List<LemmaEntity> getAllLemmaByQuery(String query, List<SiteEntity> siteEntities, LemmaFinder lemmaFinder) throws IOException {

        Set<String> lemmaSet = lemmaFinder.getLemmaSet(query);
        List<LemmaEntity> allLemmas = new ArrayList<>();
        for (String lemma : lemmaSet) {
            for (SiteEntity siteEntity : siteEntities) {
                allLemmas.add(lemmaRepositories.findBySiteIdAndLemma(siteEntity.getId(), lemma));
            }
        }

        return allLemmas;
    }

    private List<SiteEntity> getSiteEntities(String site) {
        List<SiteEntity> siteEntities = new ArrayList<>();
        if (site == null) {
            siteEntities = siteRepositories.findAll();
        } else {
            siteEntities.add(siteRepositories.findByUrl(site));
        }
        return siteEntities.stream().filter(siteEntity -> siteEntity.getStatus().equals(StatusSite.INDEXED.name())).toList();
    }

    private static void markWord(StringBuilder textFromElement, String word, int startPosition) {
        int start = textFromElement.indexOf(word, startPosition);
        if (textFromElement.indexOf("<b>", start - 3) == (start - 3)) {
            markWord(textFromElement, word, start + word.length());
            return;
        }
        int end = start + word.length();
        textFromElement.insert(start, "<b>");
        if (end == -1) {
            textFromElement.insert(textFromElement.length(), "</b>");
        } else textFromElement.insert(end + 3, "</b>");
    }
}
