package searchengine.services.crawlingpages;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.ConnectionSettings;
import searchengine.model.*;
import searchengine.repositories.IndexRepositories;
import searchengine.repositories.LemmaRepositories;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;
import searchengine.services.LemmaFinder;
import searchengine.services.interfaces.PageIndexerService;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TreeRecursive extends RecursiveTask<LinkTree> {
    private final SiteEntity site;
    private final LinkTree linkTree;
    private final ConcurrentHashMap<String, String> visitedPages;
    private final SiteRepositories siteRepositories;
    private final PageRepositories pageRepositories;
    private final LemmaRepositories lemmaRepositories;
    private final IndexRepositories indexRepositories;
    private final PageIndexerService pageIndexerService;
    private final AtomicBoolean statusIndexingProcess;

    private final ConnectionSettings connectionSettings;

    /**
     *
     * @param site индексируемый сайт
     * @param linkTree связь вложенных страниц
     * @param visitedPages посещеные страницы
     * @param siteRepositories репозторий для взаимодейсвтия с таблицей сайтов
     * @param pageRepositories репозторий для взаимодейсвтия с таблицей страниц
     * @param statusIndexingProcess статус индексации, запущена или нет
     * @param connectionSettings параметры соеденения из конфига
     * @param lemmaRepositories репозторий для взаимодейсвтия с таблицей лемм
     * @param indexRepositories репозторий для взаимодейсвтия с таблицей индексов
     * @param pageIndexerService сервис для работы с индексацией страниц
     */
    public TreeRecursive(SiteEntity site, LinkTree linkTree, ConcurrentHashMap<String, String> visitedPages,
                         SiteRepositories siteRepositories, PageRepositories pageRepositories, AtomicBoolean statusIndexingProcess,
                         ConnectionSettings connectionSettings, LemmaRepositories lemmaRepositories,
                         IndexRepositories indexRepositories, PageIndexerService pageIndexerService) {
        this.site = site;
        this.linkTree = linkTree;
        this.visitedPages = visitedPages;
        this.siteRepositories = siteRepositories;
        this.pageRepositories = pageRepositories;
        this.lemmaRepositories = lemmaRepositories;
        this.indexRepositories = indexRepositories;
        this.statusIndexingProcess = statusIndexingProcess;
        this.connectionSettings = connectionSettings;
        this.pageIndexerService =pageIndexerService;
    }

    @Override
    @Transactional
    protected LinkTree compute() throws RuntimeException {
        log.info("TreeRecursive.compute -> start");
        if (!statusIndexingProcess.get()) {
            log.warn("TreeRecursive.compute -> Indexing stopped by user: {}", linkTree.getUrl());
            throw new RuntimeException("Indexing stopped by user");
        } else if (visitedPages.get(linkTree.getUrl()) != null) {
            log.info("TreeRecursive.compute -> Already visited url: {}", linkTree.getUrl());
            return linkTree;
        }
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(linkTree.getUrl().substring(linkTree.getUrl().indexOf(".ru") + 3));
        pageEntity.setSite(site);

        ConcurrentSkipListSet<String> links = Parsing.getLinks(linkTree.getUrl(), pageEntity, connectionSettings);

        for (String link : links) {
            if (!visitedPages.containsValue(link) && !visitedPages.containsKey(link)) {
                visitedPages.put(linkTree.getUrl(), link);
                linkTree.addLink(new LinkTree(link));
            }
        }

        if (!statusIndexingProcess.get()) {
            throw new RuntimeException("Indexing stopped by user");
        }

        try {
            pageEntity = pageRepositories.save(pageEntity);
            saveLemmaAndIndexEntity(pageEntity);
        } catch (Exception e) {
            log.error("Don`t save pageEntity:{}", e);
        }
        site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
        siteRepositories.save(site);

        List<TreeRecursive> newTask = new ArrayList<>();
        for (LinkTree l : linkTree.getLinkChildren()) {
            TreeRecursive rec = new TreeRecursive(site, l, visitedPages, siteRepositories, pageRepositories, statusIndexingProcess, connectionSettings, lemmaRepositories, indexRepositories, pageIndexerService);
            rec.fork();
            newTask.add(rec);
        }
        for (TreeRecursive task : newTask) {
            task.join();
        }
        return linkTree;
    }

    /**
     * Ищет и сохраняет леммы с привязкой к странице
     * @param pageEntity объект страницы из БД
     * @throws IOException
     */
    private void saveLemmaAndIndexEntity(PageEntity pageEntity) throws IOException {
        LemmaFinder lemmaFinderRus = LemmaFinder.getRusInstance();
//        LemmaFinder lemmaFinderEng = LemmaFinder.getEngInstance();
        if (pageEntity.getCode() == 200) {
            Map<String, Integer> lemmaCollect = lemmaFinderRus.collectRusLemmas(pageEntity.getContent());
//            lemmaCollect.putAll(lemmaFinderEng.collectEngLemmas(pageEntity.getContent()));
            Set<String> lemmas = lemmaCollect.keySet();

            for (String lemma : lemmas) {
                LemmaEntity lemmaEntity = createLemmaEntity(pageEntity, lemma);
                try {
                    lemmaEntity = lemmaRepositories.save(lemmaEntity);
                } catch (Exception e){
                    log.error("Don`t save lemmaEntity:{}", e);
                }

                IndexEntity indexEntity = createIndexEntity(pageEntity, lemma, lemmaEntity, lemmaCollect);
                try {
                    indexRepositories.save(indexEntity);
                } catch (Exception e){
                    log.error("Don`t save indexEntity:{}", e);
                }
            }
        }
    }

    /**
     * Проверяет существование связки леммы и страницы в БД, если есть то инкриментирует rank если нет, то создает новую
     * @param pageEntity индексируемая страница
     * @param lemma лемма с индексируемой страницы
     * @param lemmaEntity объект леммы из БД
     * @param lemmaCollect Map лемм и их количества для индексируемой страницы
     * @return объект связки леммы и страницы для сохраннения в БД
     */
    private IndexEntity createIndexEntity(PageEntity pageEntity, String lemma, LemmaEntity lemmaEntity, Map<String, Integer> lemmaCollect) {
        IndexEntity indexEntity = indexRepositories.findByPageIdLemmaId(pageEntity.getSite().getId(), lemmaEntity.getId());
        if(indexEntity == null){
            indexEntity = new IndexEntity();
            indexEntity.setPage(pageEntity);
            indexEntity.setLemma(lemmaEntity);
            indexEntity.setRank(lemmaCollect.get(lemma));
        }else {
            indexEntity.setRank(indexEntity.getRank()+1);
        }
        return indexEntity;
    }

    /**
     * Проверяет существование леммы в БД, если есть то инкриментирует frequency если нет, то создает новую
     * @param pageEntity индексируемая страница
     * @param lemma лемма с индексируемой страницы
     * @return объект леммы для сохраннения в БД
     */
    private LemmaEntity createLemmaEntity(PageEntity pageEntity, String lemma) {
        LemmaEntity lemmaEntity = lemmaRepositories.findBySiteIdAndLemma(pageEntity.getSite().getId(), lemma);
        if (lemmaEntity == null) {
            lemmaEntity = new LemmaEntity();
            lemmaEntity.setFrequency(1);
            lemmaEntity.setSite(pageEntity.getSite());
            lemmaEntity.setLemma(lemma);
        } else {
            lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
        }
        return lemmaEntity;
    }

    /**
     * Индексация страницы
     * @param path страница
     * @throws IOException
     */
    public void indexPage(String path) throws IOException {
        PageEntity indexPage = new PageEntity();
        indexPage.setPath(path);
        indexPage.setSite(site);

        try {
            Document doc = Parsing.getJsoupDocument(site.getUrl()+path, connectionSettings);
            indexPage.setContent(doc.head() + String.valueOf(doc.body()));
            indexPage.setCode(doc.connection().response().statusCode());
            if (indexPage.getContent() == null || indexPage.getContent().isEmpty() || indexPage.getContent().isBlank()) {
                throw new Exception("Content of site id:" + indexPage.getSite().getId() + ", page:" + indexPage.getPath() + " is null or empty");
            }
        } catch (Exception ex) {
            indexPage.setCode(Parsing.getStatusCode(ex));
            SiteEntity sitePage = siteRepositories.findById(site.getId()).orElseThrow();
            sitePage.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
            siteRepositories.save(sitePage);
            pageRepositories.save(indexPage);
            return;
        }
        SiteEntity sitePage = siteRepositories.findById(site.getId()).orElseThrow();
        sitePage.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
        siteRepositories.save(sitePage);

        PageEntity pageToRefresh = pageRepositories.findBySiteIdAndPage( sitePage.getId(), path);
        if (pageToRefresh != null) {
            pageToRefresh.setCode(indexPage.getCode());
            pageToRefresh.setContent(indexPage.getContent());
            pageRepositories.save(pageToRefresh);
            pageIndexerService.refreshIndex(indexPage.getContent(), pageToRefresh);
        } else {
            pageRepositories.save(indexPage);
            pageIndexerService.refreshIndex(indexPage.getContent(), indexPage);
        }

        sitePage.setStatus(StatusSite.INDEXED.name());
        sitePage.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
        siteRepositories.save(sitePage);
    }
}
