package searchengine.services.implimentations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.ConnectionSettings;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepositories;
import searchengine.repositories.LemmaRepositories;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;
import searchengine.services.crawlingpages.ForkJoinPoolCrawlingPages;
import searchengine.services.crawlingpages.LinkTree;
import searchengine.services.crawlingpages.TreeRecursive;
import searchengine.services.interfaces.IndexingService;
import searchengine.services.interfaces.PageIndexerService;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sitesList;
    private final ConnectionSettings connectionSettings;
    private final PageRepositories pageRepositories;
    private final SiteRepositories siteRepositories;
    private final LemmaRepositories lemmaRepositories;
    private final IndexRepositories indexRepositories;
    private final PageIndexerService pageIndexerService;
    private AtomicBoolean statusIndexingProcess;

    @Override
    public void startIndexing(AtomicBoolean statusIndexingProcess) {
        log.info("startIndexing-> Start indexing process");
        this.statusIndexingProcess = statusIndexingProcess;
        IndexingResponse response = new IndexingResponse();
        try {
            deleteAllRecord();
            ForkJoinPoolCrawlingPages.crawlingPages(addNewSiteInDb(), siteRepositories, pageRepositories, this.statusIndexingProcess, connectionSettings, lemmaRepositories, indexRepositories, pageIndexerService);
            response.setResult(this.statusIndexingProcess.get());
        } catch (Exception e) {
            log.error("startIndexing<- Process stopped, because {}", e.getMessage());
            this.statusIndexingProcess.set(false);
            statusIndexingProcess.set(false);
            response.setResult(this.statusIndexingProcess.get());
            response.setError("Error: " + e);
        }
    }

    @Override
    public IndexingResponse indexPage(String url) {
        String host = url.substring(0, url.lastIndexOf(".ru") + 3);
        String path = url.replace(host, "");
        if (!host.contains("www")) {
            host = host.replace("https://", "https://www.");
        }
        SiteEntity siteEntity = siteRepositories.findByUrl(host);
        IndexingResponse indexingResponse = new IndexingResponse();

        if (!sitesList.getUrls().contains(host)) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return indexingResponse;
        } else if (siteEntity != null && siteEntity.getStatus().equals(StatusSite.INDEXING.name())) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Хост данной страницы в данный момент индексируется");
            return indexingResponse;
        }

        if (siteEntity != null) {
            siteEntity.setStatus(StatusSite.INDEXING.name());
            siteEntity.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));

        } else {
            Site site = sitesList.getSiteByUrl(host);
            siteEntity = new SiteEntity();
            siteEntity.setStatus(StatusSite.INDEXING.name());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
            siteEntity.setName(site.getName());
        }
        siteRepositories.save(siteEntity);

        statusIndexingProcess = new AtomicBoolean(true);
        try {
            TreeRecursive treeRecursive = new TreeRecursive(siteEntity, new LinkTree(url), new ConcurrentHashMap<>(),
                    siteRepositories, pageRepositories, statusIndexingProcess, connectionSettings, lemmaRepositories,
                    indexRepositories, pageIndexerService);
            treeRecursive.indexPage(path);
        } catch (Exception e) {
            statusIndexingProcess.set(false);
            siteEntity.setStatus(StatusSite.FAILED.name());
            siteEntity.setLastError(e.getMessage());
            siteRepositories.save(siteEntity);
        }
        indexingResponse.setResult(true);
        return indexingResponse;
    }

    /**
     * Сохраняет все сайты из конфига в БД
     * @return список сохраненных сайтов в БД
     */
    private List<SiteEntity> addNewSiteInDb() {
        log.info("addNewSiteInDb-> Add new record in site");
        List<SiteEntity> siteEntityList = new ArrayList<>();
        for (Site siteConfig : sitesList.getSites()) {
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setStatus(StatusSite.INDEXING.name());
            siteEntity.setUrl(siteConfig.getUrl());
            siteEntity.setStatusTime(Timestamp.valueOf(LocalDateTime.now()));
            siteEntity.setName(siteConfig.getName());
            siteEntityList.add(siteEntity);
        }
        return siteRepositories.saveAll(siteEntityList);
    }

    /**
     * Удаляем все записи из БД по урлам которые записаны в конфиге
     */
    private void deleteAllRecord() {
        log.info("deleteAllRecord-> Delete all record via page and site");
        List<SiteEntity> siteEntities = siteRepositories.findByUrls(sitesList.getUrls());
        for (SiteEntity siteEntity : siteEntities) {
            deleteAllRecordBySiteEntity(siteEntity);
        }
    }

    /**
     * Удаление всех записей из БД по сайту
     * @param siteEntity
     */
    private void deleteAllRecordBySiteEntity(SiteEntity siteEntity) {
        List<PageEntity> pageEntities = pageRepositories.findAllBySiteId(siteEntity.getId());
        List<LemmaEntity> lemmaEntities = lemmaRepositories.findBySiteId(siteEntity.getId());
        for (LemmaEntity lemmaEntity : lemmaEntities) {
            List<IndexEntity> indexEntities = indexRepositories.findByLemmaId(lemmaEntity.getId());
            for (IndexEntity indexEntity : indexEntities) {
                indexRepositories.deleteById(indexEntity.getId());
            }
            lemmaRepositories.deleteById(lemmaEntity.getId());
        }

        for (PageEntity pageEntity : pageEntities) {
            pageRepositories.deleteById(pageEntity.getId());
        }
        siteRepositories.deleteById(siteEntity.getId());
    }

}
