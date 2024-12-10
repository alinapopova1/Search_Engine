package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repositories.IndexRepositories;
import searchengine.repositories.LemmaRepositories;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageIndexerServiceImpl implements PageIndexerService{
//    private final LemmaFinder lemmaFinder;
    private final LemmaRepositories lemmaRepository;
    private final IndexRepositories indexSearchRepository;

    @Override
    public void indexHtml(String html, PageEntity indexingPage) throws IOException {
        long start = System.currentTimeMillis();
        LemmaFinder lemmaFinder = LemmaFinder.getRusInstance();
//        LemmaFinder lemmaFinderEng = LemmaFinder.getEngInstance();
        Map<String, Integer> lemmas = lemmaFinder.collectRusLemmas(html);
//        lemmas.putAll(lemmaFinderEng.collectEngLemmas(html));
        lemmas.entrySet().parallelStream().forEach(entry -> saveLemma(entry.getKey(), entry.getValue(), indexingPage));
        log.debug("Индексация страницы " + (System.currentTimeMillis() - start) + " lemmas:" + lemmas.size());
    }

    @Override
    public void refreshIndex(String html, PageEntity refreshPage) throws IOException {
        long start = System.currentTimeMillis();

        LemmaFinder lemmaFinder = LemmaFinder.getRusInstance();
//        LemmaFinder lemmaFinderEng = LemmaFinder.getEngInstance();
        Map<String, Integer> lemmas = lemmaFinder.collectRusLemmas(html);
//        lemmas.putAll(lemmaFinderEng.collectEngLemmas(html));
        //уменьшение frequency у лемм которые присутствуют на обновляемой странице
        refreshLemma(refreshPage);
        //удаление индекса
        indexSearchRepository.deleteByPageId(refreshPage.getId());
        //обновление лемм и индесов у обнолвенной страницы
        lemmas.entrySet().parallelStream().forEach(entry -> saveLemma(entry.getKey(), entry.getValue(), refreshPage));
        log.debug("Обновление индекса страницы " + (System.currentTimeMillis() - start) + " lemmas:" + lemmas.size());
    }

//    @Transactional
    private void refreshLemma(PageEntity refreshPageEntity) {
        List<IndexEntity> indexes = indexSearchRepository.findAllByPageId(refreshPageEntity.getId());
        indexes.forEach(idx -> {
            Optional<LemmaEntity> lemmaToRefresh = lemmaRepository.findById(idx.getLemma().getId());
            lemmaToRefresh.ifPresent(lemma -> {
                lemma.setFrequency(lemma.getFrequency() - idx.getRank());
                lemmaRepository.saveAndFlush(lemma);
            });
        });
    }

    @Transactional
    private void saveLemma(String k, Integer v, PageEntity pageEntity) {
        LemmaEntity existLemmaInDB = lemmaRepository.lemmaExist(k, pageEntity.getSite().getId());
        if (existLemmaInDB != null) {
            existLemmaInDB.setFrequency(existLemmaInDB.getFrequency() + v);
            lemmaRepository.saveAndFlush(existLemmaInDB);
            createIndex(pageEntity, existLemmaInDB, v);
        } else {
            try {
                LemmaEntity newLemmaToDB = new LemmaEntity();
                newLemmaToDB.setSite(pageEntity.getSite());
                newLemmaToDB.setLemma(k);
                newLemmaToDB.setFrequency(v);
                newLemmaToDB.setSite(pageEntity.getSite());
                lemmaRepository.saveAndFlush(newLemmaToDB);
                createIndex(pageEntity, newLemmaToDB, v);
            } catch (DataIntegrityViolationException ex) {
                log.debug("Ошибка при сохранении леммы, такая лемма уже существует. Вызов повторного сохранения");
                saveLemma(k, v, pageEntity);
            }
        }
    }

    private void createIndex(PageEntity pageEntity, LemmaEntity lemmaEntity, Integer rank) {
        IndexEntity indexSearchExist = indexSearchRepository.findByPageIdLemmaId(pageEntity.getId(), lemmaEntity.getId());
        if (indexSearchExist != null) {
            indexSearchExist.setRank(indexSearchExist.getRank() + rank);
            indexSearchRepository.save(indexSearchExist);
        } else {
            try {
                IndexEntity index = new IndexEntity();
                index.setPage(pageEntity);
                index.setLemma(lemmaEntity);
                index.setRank(rank);
//            index.setLemma(lemmaEntity);
//            index.setPage(pageEntity);
                indexSearchRepository.save(index);

            } catch (Exception e){
                log.error("Exception " +e.getMessage());
                e.printStackTrace();
            }
            }
    }
}
