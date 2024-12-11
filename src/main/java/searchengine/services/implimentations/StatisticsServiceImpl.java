package searchengine.services.implimentations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepositories;
import searchengine.repositories.PageRepositories;
import searchengine.repositories.SiteRepositories;
import searchengine.services.interfaces.StatisticsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final PageRepositories pageRepositories;
    private final SiteRepositories siteRepositories;
    private final LemmaRepositories lemmaRepositories;

    private final Random random = new Random();
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        String[] statuses = { "INDEXED", "FAILED", "INDEXING" };
        String[] errors = {
                "Ошибка индексации: главная страница сайта не доступна",
                "Ошибка индексации: сайт не доступен",
                ""
        };

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for(int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            SiteEntity siteEntity =siteRepositories.findByUrl(site.getUrl());
            int pages = (int) pageRepositories.findAllBySiteId(siteEntity.getId()).stream().count();
            int lemmas = (int) lemmaRepositories.findBySiteId(siteEntity.getId()).stream().count();
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(siteEntity.getStatus());
            item.setError(siteEntity.getLastError());
            item.setStatusTime(siteEntity.getStatusTime());
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
