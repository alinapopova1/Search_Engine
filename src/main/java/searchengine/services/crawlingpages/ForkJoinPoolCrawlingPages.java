package searchengine.services.crawlingpages;

import lombok.extern.slf4j.Slf4j;
import searchengine.config.ConnectionSettings;
import searchengine.model.SiteEntity;
import searchengine.model.StatusSite;
import searchengine.repositories.IndexRepositories;
import searchengine.repositories.LemmaRepositories;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;
import searchengine.services.interfaces.PageIndexerService;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ForkJoinPoolCrawlingPages {

    /**
     * Многопоточный обход всех сайтов по страницам
     * @param sitesList список индексируемых страниц сохраненных в БД
     * @param siteRepositories репозторий для взаимодейсвтия с таблицей сайтов
     * @param pageRepositories репозторий для взаимодейсвтия с таблицей страниц
     * @param statusIndexingProcess статус индексации, запущена или нет
     * @param connectionSettings параметры соеденения из конфига
     * @param lemmaRepositories репозторий для взаимодейсвтия с таблицей лемм
     * @param indexRepositories репозторий для взаимодейсвтия с таблицей индексов
     * @param pageIndexerService сервис для работы с индексацией страниц
     * @throws InterruptedException
     */
    public static void crawlingPages(List<SiteEntity> sitesList, SiteRepositories siteRepositories, PageRepositories pageRepositories,
                                     AtomicBoolean statusIndexingProcess, ConnectionSettings connectionSettings,
                                     LemmaRepositories lemmaRepositories, IndexRepositories indexRepositories,
                                     PageIndexerService pageIndexerService) throws InterruptedException {
        log.info("crawlingPages-> Start method crawling pages");
        List<Thread> indexingThreadUrlList = new ArrayList<>();
        for (SiteEntity site : sitesList) {
            Runnable indexingSite = () -> {
                indexingSite(siteRepositories, pageRepositories, statusIndexingProcess, connectionSettings, site,
                        lemmaRepositories, indexRepositories, pageIndexerService);
            };
            Thread thread = new Thread(indexingSite);
            indexingThreadUrlList.add(thread);
            thread.start();

        }
        for (Thread thread : indexingThreadUrlList) {
            thread.join();
        }
        statusIndexingProcess.set(false);
    }

    /**
     * Многопоточный обход всех страниц в рамках сайта
     * @param siteRepositories репозторий для взаимодейсвтия с таблицей сайтов
     * @param pageRepositories репозторий для взаимодейсвтия с таблицей страниц
     * @param statusIndexingProcess статус индексации, запущена или нет
     * @param connectionSettings параметры соеденения из конфига
     * @param site индексируемый сайт
     * @param lemmaRepositories репозторий для взаимодейсвтия с таблицей лемм
     * @param indexRepositories репозторий для взаимодейсвтия с таблицей индексов
     * @param pageIndexerService сервис для работы с индексацией страниц
     */
    public static void indexingSite(SiteRepositories siteRepositories, PageRepositories pageRepositories,
                                    AtomicBoolean statusIndexingProcess, ConnectionSettings connectionSettings,
                                    SiteEntity site, LemmaRepositories lemmaRepositories, IndexRepositories indexRepositories, PageIndexerService pageIndexerService) {
        log.info("indexingSite-> Start method indexing site: {}", site.getUrl());
        ConcurrentHashMap<String, String> visitedPages = new ConcurrentHashMap<>();
        LinkTree linkTree = new LinkTree(site.getUrl());
        try {
            new ForkJoinPool().invoke(new TreeRecursive(site, linkTree, visitedPages, siteRepositories, pageRepositories,
                    statusIndexingProcess, connectionSettings, lemmaRepositories, indexRepositories, pageIndexerService));
        }  catch (RuntimeException e){
            failureIndexingSiteByUser(siteRepositories, site);
        }

        if (!statusIndexingProcess.get()) {
            failureIndexingSiteByUser(siteRepositories, site);
        } else {
            log.info("indexingSite<- Indexing successful site:{}", site.getName());
            site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
            site.setStatus(StatusSite.INDEXED.name());
            siteRepositories.save(site);
        }
    }

    /**
     *
     * @param siteRepositories репозторий для взаимодейсвтия с таблицей сайтов
     * @param site индексируемый сайт
     */
    private static void failureIndexingSiteByUser(SiteRepositories siteRepositories, SiteEntity site) {
        log.warn("indexingSite<- Indexing stopped by user, site:{}", site.getName());
        site.setStatus(StatusSite.FAILED.name());
        site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
        site.setLastError("Indexing stopped by user");
        siteRepositories.save(site);
    }
}
