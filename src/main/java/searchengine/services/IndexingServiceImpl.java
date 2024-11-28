package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sitesList;
    private final ConnectionSettings connectionSettings;
    //    private final StoragePage storagePage;
//    private final StorageSite storageSite;
    private final PageRepositories pageRepositories;
    private final SiteRepositories siteRepositories;
    private final LemmaRepositories lemmaRepositories;
    private final IndexRepositories indexRepositories;
    private AtomicBoolean statusIndexingProcess;

    @Override
    public IndexingResponse startIndexing(AtomicBoolean statusIndexingProcess) {
        log.info("startIndexing-> Start indexing process");
        this.statusIndexingProcess = statusIndexingProcess;
        IndexingResponse response = new IndexingResponse();
        try {
            deleteAllRecord();
            ForkJoinPoolCrawlingPages.crawlingPages(addNewSiteInDb(), siteRepositories, pageRepositories, this.statusIndexingProcess, connectionSettings, lemmaRepositories, indexRepositories);
            response.setResult(this.statusIndexingProcess.get());
        } catch (Exception e) {
            this.statusIndexingProcess.set(false);
            statusIndexingProcess.set(false);
            response.setResult(this.statusIndexingProcess.get());
            response.setError("Error: " + e);
        }

        return response;
    }

    @Override
    public IndexingResponse indexPage(String url) {
        String host = url.substring(0, url.lastIndexOf(".ru") + 3);
        if (!host.contains("www")) {
            host = host.replace("https://", "https://www.");
        }
        SiteEntity siteEntity = siteRepositories.findByUrl(host);
        IndexingResponse indexingResponse = new IndexingResponse();
        if (siteEntity == null) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return indexingResponse;
        } else if (siteEntity.getStatus().equals(StatusSite.INDEXED.name())) {
            indexingResponse.setResult(false);
            indexingResponse.setError("Хост данной страницы в данный момент индексируется");
            return indexingResponse;
        }

        deletePage(siteEntity.getId(), url);
//        PageEntity pageEntity = new PageEntity();
//        pageEntity.setPath(url);
//        pageEntity.setSite(siteEntity);
        ForkJoinPoolCrawlingPages.indexingSite(siteRepositories, pageRepositories, new AtomicBoolean(true), connectionSettings, siteEntity, lemmaRepositories, indexRepositories);
//        LemmaFinder.lemmaFinder(url, connectionSettings, pageEntity);

        indexingResponse.setResult(true);
        return indexingResponse;
    }

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

    private void deleteAllRecord() {
        log.info("deleteAllRecord-> Delete all record via page and site");

        List<SiteEntity> siteEntities = siteRepositories.findByUrls(sitesList.getUrls());
        for (SiteEntity siteEntity : siteEntities) {
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

    private void deletePage(int siteId, String path) {
        PageEntity pageEntity = pageRepositories.findBySiteIdAndPage(siteId, path);
        if (pageEntity != null) {
            List<LemmaEntity> lemmaEntities = lemmaRepositories.findBySiteId(siteId);
            for (LemmaEntity lemmaEntity : lemmaEntities) {
                IndexEntity indexEntity = indexRepositories.findByPageIdLemmaId(pageEntity.getId(), lemmaEntity.getId());
                if (indexEntity != null) {
                    indexRepositories.deleteById(indexEntity.getId());
                    lemmaEntity.setFrequency(lemmaEntity.getFrequency() - 1);
                    lemmaRepositories.save(lemmaEntity);
                }
            }
            pageRepositories.deleteById(pageEntity.getId());
        }
    }
}
