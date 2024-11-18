package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusSite;
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
    //    private final StoragePage storagePage;
//    private final StorageSite storageSite;
    private final PageRepositories pageRepositories;
    private final SiteRepositories siteRepositories;

    @Override
    public IndexingResponse startIndexing(AtomicBoolean statusIndexingProcess) {
        log.info("startIndexing-> Start indexing process");
        IndexingResponse response = new IndexingResponse();
        try {
            deleteAllRecord();
            ForkJoinPoolCrawlingPages.crawlingPages(addNewSiteInDb(), siteRepositories, pageRepositories, statusIndexingProcess);
            response.setResult(statusIndexingProcess.get());
        } catch (Exception e) {
            response.setResult(false);
            response.setError("Error: " + e);
        }


        return response;
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
        siteRepositories.findAll().forEach(siteEntity -> {
            if (sitesList.getSites().stream().map(Site::getUrl).toList().contains(siteEntity.getUrl())) {
                PageEntity pageEntity = pageRepositories.findBySiteId(siteEntity.getId());
                if (pageEntity != null) {
                    pageRepositories.deleteById(pageEntity.getId());
                }
                siteRepositories.deleteById(siteEntity.getId());
            }
        });
    }
}
