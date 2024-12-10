package searchengine.services.implimentations;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final SiteRepositories siteRepositories;
    private final PageRepositories pageRepositories;
    private final IndexRepositories indexRepositories;
    private final LemmaRepositories lemmaRepositories;

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) throws IOException {
        log.info("search-> start method search");
        List<SiteEntity> siteEntities = getSiteEntities(site);
        LemmaFinder lemmaFinder = LemmaFinder.getRusInstance();
        LemmaFinder lemmaFinderEng = LemmaFinder.getEngInstance();
        List<LemmaEntity> allLemmas = getAllLemmaByQuery(query, siteEntities, lemmaFinder, lemmaFinderEng);
        log.info("search: Find lemmas by query: {}", allLemmas);

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
        log.info("generateResultSearchDates-> start method");
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
        log.info("getSnippet-> start method");
        text = Jsoup.clean(text, Safelist.none());
        StringBuilder textWithLemmas = new StringBuilder();
        TreeMap<Integer, String> positionsFirstHighlightedWords = new TreeMap<>();
        String lemma = "";
        List<String> addedLemmas = new ArrayList<>();

        StringBuilder word = new StringBuilder();
        for (char symbol : text.toCharArray()) {
            textWithLemmas.append(symbol);
            if (Character.isLetter(symbol)) {
                word.append(symbol);
            } else {
                try {
                    lemma = getLemmaByWord(word, lemmaFinder);

                    if (containsLemmaInListLemmaEntities(sortedLemmas, lemma)) {
                        boldingLemmaInText(textWithLemmas, word, positionsFirstHighlightedWords, addedLemmas, lemma);
                    }
                    word = new StringBuilder();
                } catch (WrongCharaterException wce) {
//                    try {
//                        lemma = luceneMorphEng.getNormalForms(lemma).get(0);
//                        if (lemmas.contains(lemma)) {
//                            insertInPositionsWords(textWithLemmas, word, positionsFirstHighlightedWords, addedLemmas, lemma);
//                        }
                    word = new StringBuilder();
//                    } catch (WrongCharaterException w) {
//                        word = new StringBuilder();
//                    }
                }
            }
        }
        return createSnippet(positionsFirstHighlightedWords, textWithLemmas);
    }

    private static String getLemmaByWord(StringBuilder word, LemmaFinder lemmaFinder) {
        String wordLowerCase;
        if (!word.isEmpty()) {
            wordLowerCase = word.toString().toLowerCase();
        } else {
            wordLowerCase = ".";
        }
        return lemmaFinder.getLemmaByWord(wordLowerCase);
    }

    /**
     * Проверяет вхождение леммы в отсортированный список лемм из БД
     *
     * @param sortedLemmas
     * @param lemma
     * @return
     */
    private static boolean containsLemmaInListLemmaEntities(List<LemmaEntity> sortedLemmas, String lemma) {
        List<String> lemmas = new ArrayList<>();
        for (LemmaEntity sortedLemma : sortedLemmas) {
            lemmas.add(sortedLemma.getLemma());
        }
        return lemmas.contains(lemma);
    }

    /**
     *
     * @param positionsFirstHighlightedWords индекс первого вхождения искомого слова в текст
     * @param textWithLemmas текст со страницы с леммами
     * @return сниппет
     */
    public static StringBuilder createSnippet(TreeMap<Integer, String> positionsFirstHighlightedWords, StringBuilder textWithLemmas) {
        log.info("createSnippet-> start method");
        StringBuilder snippet = new StringBuilder();
        int prevPosition = 0;
        int startPosition;
        int endPosition;
        int endIndexHighlightedWord;
        int countSymbolsFromLemma = Math.max(160 / positionsFirstHighlightedWords.size(), 25);

        for (Map.Entry<Integer, String> positionHighlightedWord : positionsFirstHighlightedWords.entrySet()) {
            endIndexHighlightedWord =positionHighlightedWord.getKey() + positionHighlightedWord.getValue().length();

            if (prevPosition <= endIndexHighlightedWord) {
                startPosition = Math.max(positionHighlightedWord.getKey() - countSymbolsFromLemma, 0);
                endPosition = Math.min(endIndexHighlightedWord + countSymbolsFromLemma, textWithLemmas.length() - 1);

                String subText = textWithLemmas.substring(startPosition, endPosition);
                subText = getSubTextTrim(startPosition, subText);
                getSubTextWithThreeDots(textWithLemmas, endPosition, subText, snippet);
                prevPosition = endIndexHighlightedWord - 1 +countSymbolsFromLemma;
            }
        }
        return new StringBuilder(snippet.toString()).append(". . .");
    }

    /**
     * Добавляет три точки в снипет
     * @param textWithLemmas
     * @param endPosition
     * @param subText
     * @param snippet
     */
    private static void getSubTextWithThreeDots(StringBuilder textWithLemmas, int endPosition, String subText, StringBuilder snippet) {
        if (endPosition != textWithLemmas.length() - 1) {
            int index = subText.lastIndexOf('\s');
            subText = subText.substring(0, index);
            snippet.append(". . .").append(subText);
        }
    }

    /**
     * Убирает лишнии пробелы из текста
     * @param startPosition
     * @param subText
     * @return
     */
    private static String getSubTextTrim(int startPosition, String subText) {
        if (startPosition != 0) {
            String split = subText.split("\\s", 2)[0];
            subText = subText.substring(split.length()).trim();
        }
        return subText;
    }

    /**
     * Получение части текста с леммой + выделение жирным леммы
     * @param textWithLemmas найденный текст включая последнюю лемму
     * @param word слово в тексте подходящие под искомую лемму
     * @param positionsFirstHighlightedWords
     * @param addedLemmas
     * @param lemma
     */
    public static void boldingLemmaInText(StringBuilder textWithLemmas, StringBuilder word, TreeMap<Integer,
            String> positionsFirstHighlightedWords, List<String> addedLemmas, String lemma) {
        String textBeforeLemma = textWithLemmas.substring(0, textWithLemmas.length() - word.length() - 1);
        textWithLemmas.delete(0, textWithLemmas.length()).append(textBeforeLemma);
        word.insert(0, "<b>").append("</b> ");
        textWithLemmas.append(word);
        if (!addedLemmas.contains(lemma)) {
            addedLemmas.add(lemma);
            positionsFirstHighlightedWords.put(textWithLemmas.indexOf(word.toString()), word.toString());
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
     * @param absRelevantPages Map страница кандидат со своей абсолютной релевантностью
     * @return Map страница кандидат со своей релевантностью
     */
    private static HashMap<PageEntity, Float> calculateAllRelevant(HashMap<PageEntity, Float> absRelevantPages) {
        log.info("calculateAllRelevant-> start method");
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
     * @return Map страница кандидат со своей абсолютной релевантностью
     */
    private static HashMap<PageEntity, Float> calculateAllAbsRelevant(List<PageEntity> pageEntities, List<IndexEntity> indexEntities) {
        log.info("calculateAllAbsRelevant-> start method");
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
        log.info("getSortedLemmasByFrequency-> start method");
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
        log.info("getAllLemmaByQuery-> start method search lemma by query");
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

    /**
     * Получаем сайты по котором будем делать поиск
     * @param site
     * @return
     */
    private List<SiteEntity> getSiteEntities(String site) {
        List<SiteEntity> siteEntities = new ArrayList<>();
        if (site == null) {
            siteEntities = siteRepositories.findAll();
        } else {
            siteEntities.add(siteRepositories.findByUrl(site));
        }
        return siteEntities.stream().filter(siteEntity -> siteEntity.getStatus().equals(StatusSite.INDEXED.name())).toList();
    }
}
