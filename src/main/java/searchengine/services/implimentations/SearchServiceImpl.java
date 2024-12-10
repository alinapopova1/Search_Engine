package searchengine.services.implimentations;


import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.WrongCharaterException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepositories;
import searchengine.repositories.LemmaRepositories;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;
import searchengine.services.LemmaFinder;
import searchengine.services.interfaces.SearchService;

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
        LemmaFinder lemmaFinderEng = LemmaFinder.getEngInstance();
        List<LemmaEntity> allLemmas = getAllLemmaByQuery(query, siteEntities, lemmaFinder, lemmaFinderEng);

        SearchResponse searchResponse = new SearchResponse();
        if (allLemmas.isEmpty()) {
            return getSearchResponseEmpty(searchResponse);
        }

        List<LemmaEntity> sortedLemmas = getSortedLemmasByFrequency(allLemmas);

        List<IndexEntity> indexEntities = new ArrayList<>();
        List<Integer> pageIdsToSave = new ArrayList<>();
        getPageToSave(sortedLemmas, indexEntities, pageIdsToSave);

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

    /**
     * Наполняет список id-шниками страниц - кандидаты к результату поиска по леммам
     *
     * @param sortedLemmas  отсортированные леммы по порядку увеличения частоты встречаемости
     * @param indexEntities Список полученных связок лемм и страниц
     * @param pageIdsToSave список id страниц, кандидатов к результату поиска по леммам
     */
    private void getPageToSave(List<LemmaEntity> sortedLemmas, List<IndexEntity> indexEntities, List<Integer> pageIdsToSave) {
        List<IndexEntity> indexEntity;
        for (LemmaEntity lemmaEntity : sortedLemmas) {
            indexEntity = indexRepositories.findByLemmaId(lemmaEntity.getId());
            indexEntities.addAll(indexEntity);
            if (lemmaEntity == sortedLemmas.get(0)) {
                indexEntities.addAll(indexRepositories.findByLemmaId(sortedLemmas.get(0).getId()));
                pageIdsToSave.addAll(getPageIdsFromIndex(indexEntities));
            } else {
                indexEntities.addAll(indexRepositories.findByPageIdsLemmaId(pageIdsToSave, lemmaEntity.getId()));
                pageIdsToSave.addAll(getPageIdsFromIndex(indexEntities));
            }
        }
    }

    /**
     * Получить пустой ответ, чтобы прокинуть на фронт
     *
     * @param searchResponse
     * @return
     */
    private static SearchResponse getSearchResponseEmpty(SearchResponse searchResponse) {
        searchResponse.setResult(true);
        searchResponse.setCount(0);
        return searchResponse;
    }

    /**
     * @param relevantPages Map страница кандидат со своей релевантностью
     * @param siteEntities  список страниц, по которым искали
     * @param lemmaFinder   сервис для поиска лемм
     * @param sortedLemmas  список отсортированных лемм
     * @return список с результатом поиска по страницам
     */
    private static List<SearchData> generateResultSearchDates(HashMap<PageEntity, Float> relevantPages, List<SiteEntity> siteEntities,
                                                              LemmaFinder lemmaFinder, List<LemmaEntity> sortedLemmas) {
        List<SearchData> resultSearchDates = new ArrayList<>();

        for (PageEntity pageEntity : relevantPages.keySet()) {
            Document document = Jsoup.parse(pageEntity.getContent());

            SiteEntity searchSite = new SiteEntity();
            for (SiteEntity siteEntity : siteEntities) {
                if (siteEntity.getId() == pageEntity.getSite().getId()) {
                    searchSite = siteEntity;
                }
            }
            SearchData searchData = new SearchData();
            searchData.setUri(pageEntity.getPath());
            searchData.setSite(searchSite.getUrl());
            searchData.setTitle(document.title());
            searchData.setSiteName(searchSite.getName());
            searchData.setSnippet(getSnippet(document.text(), lemmaFinder, sortedLemmas).toString());
            searchData.setRelevance(relevantPages.get(pageEntity));
            resultSearchDates.add(searchData);
        }

        return resultSearchDates;
    }

    /**
     * Получение сниппета по странице, которая была найдена в результате поиска
     *
     * @param text         контент страницы, которая была найдена в результате поиска
     * @param lemmaFinder  сервис для поиска лемм
     * @param sortedLemmas список отсортированных лемм
     * @return сниппет по странице, которая была найдена в результате поиска
     */
    private static StringBuilder getSnippet(String text, LemmaFinder lemmaFinder, List<LemmaEntity> sortedLemmas) {
        text = Jsoup.clean(text, Safelist.none());
        StringBuilder textNearLemma = new StringBuilder();
        TreeMap<Integer, String> positionsWords = new TreeMap<>();
        String lemma = "";
        List<String> addedLemmas = new ArrayList<>();

        StringBuilder word = new StringBuilder();
        for (char symbol : text.toCharArray()) {
            textNearLemma.append(symbol);
            if (Character.isLetter(symbol)) {
                word.append(symbol);
            } else {
                try {
//                    if (!word.isEmpty()) {
//                        lemma = word.toString().toLowerCase();
//                    } else {
//                        lemma = ".";
//                    }
                    lemma = !word.isEmpty() ? word.toString().toLowerCase() : ".";
                    lemma = lemmaFinder.getLemmaByWord(lemma);
                    if (sortedLemmas.stream().map(LemmaEntity::getLemma).toList().contains(lemma)) {
                        insertInPositionsWords(textNearLemma, word, positionsWords, addedLemmas, lemma);
                    }
                    word = new StringBuilder();
                } catch (WrongCharaterException wce) {
//                    try {
//                        lemma = luceneMorphEng.getNormalForms(lemma).get(0);
//                        if (lemmas.contains(lemma)) {
//                            insertInPositionsWords(textNearLemma, word, positionsWords, addedLemmas, lemma);
//                        }
                    word = new StringBuilder();
//                    } catch (WrongCharaterException w) {
//                        word = new StringBuilder();
//                    }
                }
            }
        }
        return createSnippet(positionsWords, textNearLemma);
    }

    public static StringBuilder createSnippet(TreeMap<Integer, String> positionsWords, StringBuilder textNearLemma) {
        String resultText = "";
        int countSymbolsInPartSnippet = Math.max(150 / positionsWords.size(), 20);
        int prevPosition = -countSymbolsInPartSnippet;
        for (Map.Entry<Integer, String> positionLemma : positionsWords.entrySet()) {
            if (prevPosition + countSymbolsInPartSnippet <= positionLemma.getKey() + positionLemma.getValue().length()) {
                int startPosition = Math.max(positionLemma.getKey() - countSymbolsInPartSnippet, 0);
                int endPosition = Math.min(positionLemma.getKey() + positionLemma.getValue().length()
                        + countSymbolsInPartSnippet, textNearLemma.length() - 1);
                String partText = textNearLemma.substring(startPosition, endPosition);
                if (startPosition != 0) {
                    String split = partText.split("\\s", 2)[0];
                    partText = partText.substring(split.length()).trim();
                }
                if (endPosition != textNearLemma.length() - 1) {
                    int index = partText.lastIndexOf('\s');
                    partText = partText.substring(0, index);
                    resultText = resultText + (". . .") + partText;
                }
                prevPosition = positionLemma.getKey() + positionLemma.getValue().length() - 1;
            }
        }
        return new StringBuilder(resultText).append(". . .");
    }

    public static void insertInPositionsWords(StringBuilder textNearLemma, StringBuilder word, TreeMap<Integer,
            String> positionsWords, List<String> addedLemmas, String lemma) {
        String stringBuilder = textNearLemma.substring(0, textNearLemma.length() - word.length() - 1);
        textNearLemma.delete(0, textNearLemma.length()).append(stringBuilder);
        word.insert(0, "<b>").append("</b> ");
        textNearLemma.append(word);
        if (!addedLemmas.contains(lemma)) {
            addedLemmas.add(lemma);
            positionsWords.put(textNearLemma.indexOf(word.toString()), word.toString());
        }
    }

    /**
     * Сортировка результата поиска по offset
     *
     * @param resultSearchDates результат поиска
     * @param offset
     * @param limit
     * @return отсортированный результат
     */
    private static List<SearchData> getSortedSearchDataWithOffset(List<SearchData> resultSearchDates, int offset, int limit) {
        List<SearchData> sortedSearchDates = resultSearchDates.stream().sorted(Comparator.comparingDouble(SearchData::getRelevance).reversed()).toList();
        List<SearchData> result = new ArrayList<>();
        for (int i = offset; i <= offset + limit; i++) {
            try {
                result.add(sortedSearchDates.get(i));
            } catch (IndexOutOfBoundsException exception) {
                break;
            }
        }
        return result;
    }

    /**
     * Рассчет релевантности для каждой страницы
     *
     * @param absRelevantPages Map страница кандидат со своей абсалютной релевантностью
     * @return Map страница кандидат со своей релевантностью
     */
    private static HashMap<PageEntity, Float> calculateAllRelevant(HashMap<PageEntity, Float> absRelevantPages) {
        Float maxRelevant = absRelevantPages.values().stream().max(Float::compareTo).get();
        HashMap<PageEntity, Float> relevantPages = new HashMap<>();
        for (PageEntity indexPage : absRelevantPages.keySet()) {
            relevantPages.put(indexPage, absRelevantPages.get(indexPage) / maxRelevant);
        }
        return relevantPages;
    }

    /**
     * Рассчет абсолютной релевантности
     *
     * @param pageEntities  список всех страниц кандидатов после поиска
     * @param indexEntities список всех связок страниц кандидатов и их лемм
     * @return Map страница кандидат со своей абсалютной релевантностью
     */
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

    /**
     * Сортировка лемм по частоте встречаемости - от самых редких до самых частых
     *
     * @param allLemmas все найденные леммы в БД по искомому выражению
     * @return
     */
    private static List<LemmaEntity> getSortedLemmasByFrequency(List<LemmaEntity> allLemmas) {
        return allLemmas.stream()
                .map(lemmaEntity -> new AbstractMap.SimpleEntry<>(lemmaEntity.getFrequency(), lemmaEntity))
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(Map.Entry::getValue).toList();
    }

    /**
     * Поиск всех лемм по выражению к привязки к сайтам
     *
     * @param query          икомое выражение
     * @param siteEntities   сайты на которых искать
     * @param lemmaFinder    сервис для поиска лемм
     * @param lemmaFinderEng сервис для поиска лемм
     * @return список всех лемм
     * @throws IOException
     */
    private List<LemmaEntity> getAllLemmaByQuery(String query, List<SiteEntity> siteEntities, LemmaFinder lemmaFinder, LemmaFinder lemmaFinderEng) throws IOException {
        Set<String> lemmaSet = lemmaFinder.getRusLemmaSet(query);
//        lemmaSet.addAll(lemmaFinderEng.getEngLemmaSet(query));
        List<LemmaEntity> allLemmas = new ArrayList<>();
        for (String lemma : lemmaSet) {
            for (SiteEntity siteEntity : siteEntities) {
                LemmaEntity lemmaEntity = lemmaRepositories.findBySiteIdAndLemma(siteEntity.getId(), lemma);
                if (lemmaEntity != null) {
                    allLemmas.add(lemmaEntity);
                }
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
//        if (textFromElement.indexOf("<b>", start - 3) == (start - 3)) {
//            markWord(textFromElement, word, start + word.length());
//            return;
//        }
        int end = textFromElement.indexOf(word) + word.length();
        if (start >= 0) {
            textFromElement.insert(start, "<b>");
        } else {
            textFromElement.insert(0, "<b>");
        }


        if (end == -1) {
            textFromElement.insert(textFromElement.length(), "</b>");
        } else textFromElement.insert(end + 3, "</b>");
    }
//
//    public static String getSnippetFromPage(String text, List<String> searchQuery) {
//        List<String> queryLocalCopy = new ArrayList<>(searchQuery);
//        List<String> words = List.of(text.split("\\b"));
//        ListIterator<String> iterator;
//        List<Integer> foundWordsIndexes = new ArrayList<>();
//
//        for (String word : words) {
//            if(queryLocalCopy.isEmpty()) {
//                break;
//            }
//            iterator = queryLocalCopy.listIterator();
//            while(iterator.hasNext()) {
//                List<String> wordNormalForm = new ArrayList<>(ms.getNormalFormOfAWord(word.toLowerCase(Locale.ROOT)));
//                wordNormalForm.retainAll(ms.getNormalFormOfAWord(iterator.next()));
//                if(wordNormalForm.isEmpty()) {
//                    continue;
//                }
//                foundWordsIndexes.add(words.indexOf(word));
//                iterator.remove();
//            }
//        }
//
//        return constructSnippetWithHighlight(foundWordsIndexes, new ArrayList<>(words));
//    }
//
//    public static String constructSnippetWithHighlight(List<Integer> foundWordsIndexes, List<String> words) {
//        List<String> snippetCollector = new ArrayList<>();
//        int beginning, end, before, after, index, prevIndex;
//        before = 12;
//        after = 6;
//
//        foundWordsIndexes.sort(Integer::compareTo);
//
//        for(int i : foundWordsIndexes) {
//            words.set(i, "<b>" + words.get(i) + "</b>");
//        }
//
//        index = foundWordsIndexes.get(0);
//        beginning = Math.max(0, index - before);
//        end = Math.min(words.size() - 1, index + after);
//
//        for (int i = 1; i <= foundWordsIndexes.size(); i++) {
//            if(i == foundWordsIndexes.size()) {
//                snippetCollector.add(String.join("", words.subList(beginning, end)));
//                break;
//            }
//            prevIndex = index;
//            index = foundWordsIndexes.get(i);
//            if(index - before <= prevIndex) {
//                end = Math.min(words.size() - 1, index + after);
//                continue;
//            }
//            snippetCollector.add(String.join("", words.subList(beginning, end)));
//            beginning = Math.max(0, index - before);
//            end = Math.min(words.size() - 1, index + after);
//        }
//        return String.join("...", snippetCollector);
//    }
}
