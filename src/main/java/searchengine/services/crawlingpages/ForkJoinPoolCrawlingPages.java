package searchengine.services.crawlingpages;

import lombok.extern.slf4j.Slf4j;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusSite;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ForkJoinPoolCrawlingPages {

    public static void crawlingPages(List<SiteEntity> sitesList, SiteRepositories siteRepositories, PageRepositories pageRepositories, AtomicBoolean statusIndexingProcess) throws InterruptedException {
        log.info("crawlingPages-> Start method crawling pages");
        List<Thread> indexingThreadUrlList = new ArrayList<>();
        for (SiteEntity site: sitesList){
            Runnable indexingSite = ()-> {
                ConcurrentHashMap<String, String> visitedPages = new ConcurrentHashMap<>();
                LinkTree linkTree = new LinkTree(site.getUrl());
                try {
                    log.info("crawlingPages-> Start process crawling pages");
                    new ForkJoinPool().invoke(new TreeRecursive(site, linkTree, visitedPages, siteRepositories, pageRepositories, statusIndexingProcess));
                } catch (Exception e){
                    log.warn("crawlingPages-> Exception " + e);
                    site.setStatus(StatusSite.FAILED.name());
                    site.setLastError(e.getMessage());
                    site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
                    siteRepositories.save(site);
                }

                if (!statusIndexingProcess.get()){
                    log.warn("crawlingPages-> Indexing stopped by user, site:" + site.getName());
                    site.setStatus(StatusSite.FAILED.name());
                    site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
                    site.setLastError("Indexing stopped by user");
                    siteRepositories.save(site);
                } else {
                    log.info("crawlingPages-> Indexing successful site:" + site.getName());
                    site.setStatus(StatusSite.INDEXED.name());
                    site.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
                    siteRepositories.save(site);
//                    List<PageEntity> pageEntities = new ArrayList<>();
//                    for (String link: linkTree.getAllChildrenLink()){
//                        PageEntity pageEntity = new PageEntity();
//                        pageEntity.setPath();
//                        pageEntities.add(pageEntity);
//                    }
//                    pageRepositories.saveAll(pageEntities);
                }
            };
            Thread thread = new Thread(indexingSite);
            indexingThreadUrlList.add(thread);
            thread.start();
        }
        for (Thread thread: indexingThreadUrlList){
            thread.join();
        }
        statusIndexingProcess.set(false);
    }
}
