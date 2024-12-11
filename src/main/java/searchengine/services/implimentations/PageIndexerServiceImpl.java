package searchengine.services.implimentations;

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
import searchengine.services.LemmaFinder;
import searchengine.services.interfaces.PageIndexerService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageIndexerServiceImpl implements PageIndexerService {
    private final LemmaRepositories lemmaRepository;
    private final IndexRepositories indexSearchRepository;

    @Override
    public void refreshIndex(String html, PageEntity refreshPage) throws IOException {
        log.info("refreshIndex-> Start refresh index page:{}", refreshPage.getPath());

        LemmaFinder lemmaFinder = LemmaFinder.getRusInstance();
        LemmaFinder lemmaFinderEng = LemmaFinder.getEngInstance();
        Map<String, Integer> lemmas = lemmaFinder.collectRusLemmas(html);
        lemmas.putAll(lemmaFinderEng.collectEngLemmas(html));
        refreshLemma(refreshPage);
        indexSearchRepository.deleteByPageId(refreshPage.getId());
        lemmas.entrySet().parallelStream().forEach(entry -> saveLemma(entry.getKey(), entry.getValue(), refreshPage));
    }

    /**
     * Обновление Frequency у лемм для переиндексируемой страницы
     * @param refreshPageEntity переиндексируемая страница
     */
    private void refreshLemma(PageEntity refreshPageEntity) {
        log.info("refreshLemma-> start refresh lemma");
        List<IndexEntity> indexes = indexSearchRepository.findAllByPageId(refreshPageEntity.getId());
        indexes.forEach(index -> {
            Optional<LemmaEntity> lemmaToRefresh = lemmaRepository.findById(index.getLemma().getId());
            lemmaToRefresh.ifPresent(lemma -> {
                lemma.setFrequency(lemma.getFrequency() - index.getRank());
                lemmaRepository.saveAndFlush(lemma);
            });
        });
    }

    /**
     * Сохранение лемм для переиндексированной страницы
     * @param lemmaName лемма
     * @param frequencyLem количество лемм на сайте
     * @param pageEntity переиндексируемая страница
     */
    @Transactional
    private void saveLemma(String lemmaName, Integer frequencyLem, PageEntity pageEntity) {
        log.info("saveLemma-> start save lemma");
        LemmaEntity existLemmaInDB = lemmaRepository.lemmaExist(lemmaName, pageEntity.getSite().getId());
        if (existLemmaInDB != null) {
            existLemmaInDB.setFrequency(existLemmaInDB.getFrequency() + frequencyLem);
            lemmaRepository.saveAndFlush(existLemmaInDB);
            createIndex(pageEntity, existLemmaInDB, frequencyLem);
        } else {
            try {
                LemmaEntity newLemmaToDB = new LemmaEntity();
                newLemmaToDB.setSite(pageEntity.getSite());
                newLemmaToDB.setLemma(lemmaName);
                newLemmaToDB.setFrequency(frequencyLem);
                newLemmaToDB.setSite(pageEntity.getSite());
                lemmaRepository.saveAndFlush(newLemmaToDB);
                createIndex(pageEntity, newLemmaToDB, frequencyLem);
            } catch (DataIntegrityViolationException ex) {
                log.debug("This lemma already exist");
                saveLemma(lemmaName, frequencyLem, pageEntity);
            }
        }
    }

    /**
     * Проверяет существование связки леммы и страницы в БД, если есть то инкриментирует rank если нет, то создает новую
     * @param pageEntity
     * @param lemmaEntity
     * @param rank
     */
    private void createIndex(PageEntity pageEntity, LemmaEntity lemmaEntity, Integer rank) {
        log.info("createIndex-> start create index");
        IndexEntity indexSearchExist = indexSearchRepository.findByPageIdLemmaId(pageEntity.getId(), lemmaEntity.getId());
        if (indexSearchExist != null) {
            indexSearchExist.setRank(indexSearchExist.getRank() + rank);
            indexSearchRepository.save(indexSearchExist);
        } else {
            try {
                IndexEntity indexEntity = new IndexEntity();
                indexEntity.setPage(pageEntity);
                indexEntity.setLemma(lemmaEntity);
                indexEntity.setRank(rank);
                indexSearchRepository.save(indexEntity);
            } catch (Exception e) {
                log.error("Don`t save indexEntity:{}", e);
            }
        }
    }
}
